package pe.upn.visorpacs.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.upn.visorpacs.auditoria.AuditoriaService;
import pe.upn.visorpacs.auditoria.ResultadoEvento;
import pe.upn.visorpacs.auditoria.TipoEvento;
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Permisos;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.UsuarioRepository;
import pe.upn.visorpacs.sesion.EstadoCuentaCache;
import pe.upn.visorpacs.sesion.RevocacionTokens;
import pe.upn.visorpacs.sesion.SesionDuracionProperties;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String CLAVE = "ClaveCorrecta-123";
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneOffset.UTC);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // rápido para pruebas
    @Mock UsuarioRepository usuarios;
    @Mock JwtService jwtService;
    @Mock AuditoriaService auditoria;
    @Mock EstadoCuentaCache cache;
    @Mock RevocacionTokens revocaciones;
    private AuthService servicio;
    private Usuario medico;

    @BeforeEach
    void preparar() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x", "visor-pacs", 15),
                new AppProperties.Security(3, 15), // 3 intentos para acortar las pruebas
                new AppProperties.Orthanc("http://orthanc:8042", "u", "p"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Bootstrap(false, "", "", "", "", "", "", "", "", ""));
        servicio = new AuthService(usuarios, encoder, jwtService, props, clock, auditoria, cache,
                revocaciones, new SesionDuracionProperties(8));
        medico = Usuario.nuevo("medico.demo", encoder.encode(CLAVE), "Ana", "Pérez",
                "ana@example.com", new Rol("MEDICO"));
        medico.otorgar(new Permiso(Permisos.ACCESO_ESTUDIOS, "Acceso a estudios"));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    @Test
    void accesoValidoEmiteTokenYReiniciaIntentos() {
        medico.setIntentosFallidos(2);
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));
        when(jwtService.issue(medico)).thenReturn(new IssuedToken("tok", 900, "MEDICO", "Ana Pérez"));

        IssuedToken token = servicio.login("medico.demo", CLAVE);

        assertEquals("tok", token.value());
        assertEquals(0, medico.getIntentosFallidos());
        verify(usuarios).save(medico);
    }

    @Test
    void claveIncorrectaSumaUnIntentoYRechazaConMensajeGenerico() {
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));

        assertThrows(InvalidCredentialsException.class, () -> servicio.login("medico.demo", "otra-clave"));

        assertEquals(1, medico.getIntentosFallidos());
        verify(usuarios).save(medico);
        verify(jwtService, never()).issue(any());
    }

    @Test
    void alSuperarElMaximoDeIntentosLaCuentaQuedaBloqueada() {
        medico.setIntentosFallidos(2);
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));

        assertThrows(InvalidCredentialsException.class, () -> servicio.login("medico.demo", "otra-clave"));

        assertTrue(medico.estaBloqueado(ahora()));
        assertEquals(0, medico.getIntentosFallidos());
    }

    @Test
    void cuentaBloqueadaRechazaAunConClaveCorrecta() {
        medico.setBloqueadoHasta(ahora().plusMinutes(5));
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));

        assertThrows(AccountLockedException.class, () -> servicio.login("medico.demo", CLAVE));

        verify(jwtService, never()).issue(any());
    }

    @Test
    void bloqueoVencidoPermiteIngresar() {
        medico.setBloqueadoHasta(ahora().minusMinutes(1));
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));
        when(jwtService.issue(medico)).thenReturn(new IssuedToken("tok", 900, "MEDICO", "Ana Pérez"));

        servicio.login("medico.demo", CLAVE);

        assertFalse(medico.estaBloqueado(ahora()));
    }

    @Test
    void usuarioInexistenteDaCredencialesInvalidas() {
        when(usuarios.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> servicio.login("nadie", CLAVE));

        verify(usuarios, never()).save(any());
    }

    @Test
    void cuentaInactivaDaCredencialesInvalidas() {
        medico.setActivo(false);
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));

        assertThrows(InvalidCredentialsException.class, () -> servicio.login("medico.demo", CLAVE));

        verify(jwtService, never()).issue(any());
    }

    // ---- HU10 (CA10.2): cada inicio de sesión, exitoso o fallido, queda registrado ----

    @Test
    void accesoValidoSeAudita() {
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));
        when(jwtService.issue(medico)).thenReturn(new IssuedToken("tok", 900, "MEDICO", "Ana Pérez"));

        servicio.login("medico.demo", CLAVE);

        verify(auditoria).registrar(TipoEvento.INICIO_SESION, ResultadoEvento.EXITO, medico, null, null);
    }

    @Test
    void claveIncorrectaSeAuditaComoFallo() {
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));

        assertThrows(InvalidCredentialsException.class, () -> servicio.login("medico.demo", "otra-clave"));

        verify(auditoria).registrar(TipoEvento.INICIO_SESION, ResultadoEvento.FALLO, medico, null, "clave incorrecta");
    }

    @Test
    void usuarioInexistenteSeAuditaConElNombreIntentado() {
        when(usuarios.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> servicio.login("nadie", CLAVE));

        verify(auditoria).registrar(eq(TipoEvento.INICIO_SESION), eq(ResultadoEvento.FALLO), isNull(),
                eq("nadie"), isNull(), isNull(), eq("usuario inexistente"));
    }

    @Test
    void cuentaBloqueadaSeAuditaComoFallo() {
        medico.setBloqueadoHasta(ahora().plusMinutes(5));
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));

        assertThrows(AccountLockedException.class, () -> servicio.login("medico.demo", CLAVE));

        verify(auditoria).registrar(TipoEvento.INICIO_SESION, ResultadoEvento.FALLO, medico, null, "cuenta bloqueada");
    }

    // ---- HU08 (CA08.3): sin permisos no entra, y el mensaje no lo revela ----

    @Test
    void medicoSinPermisoDeEstudiosNoPuedeIniciarSesion() {
        medico.revocar(Permisos.ACCESO_ESTUDIOS);
        when(usuarios.findByUsername("medico.demo")).thenReturn(Optional.of(medico));

        assertThrows(InvalidCredentialsException.class, () -> servicio.login("medico.demo", CLAVE));

        verify(jwtService, never()).issue(any());
        verify(auditoria).registrar(TipoEvento.INICIO_SESION, ResultadoEvento.FALLO, medico, null, "sin permiso de acceso");
    }

    @Test
    void administradorEntraSinNecesitarPermisoDeEstudios() {
        Usuario admin = Usuario.nuevo("admin.demo", encoder.encode(CLAVE), "Luz", "Rojas",
                "luz@example.com", new Rol("ADMIN"));
        when(usuarios.findByUsername("admin.demo")).thenReturn(Optional.of(admin));
        when(jwtService.issue(admin)).thenReturn(new IssuedToken("tok", 900, "ADMIN", "Luz Rojas"));

        assertEquals("tok", servicio.login("admin.demo", CLAVE).value());
    }

    // ---- HU12: cierre de sesión y renovación ----

    @Test
    void cerrarSesionSubeLaVersionInvalidaLaMemoriaYAudita() {
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
        int antes = medico.getSesionVersion();

        servicio.cerrarSesion(7);

        assertEquals(antes + 1, medico.getSesionVersion());
        verify(usuarios).save(medico);
        verify(cache).invalidarTrasCommit(7);
        verify(auditoria).registrar(TipoEvento.CIERRE_SESION, ResultadoEvento.EXITO, medico, null, null);
    }

    @Test
    void cerrarSesionDeUnaCuentaInexistenteDaSesionNoVigente() {
        when(usuarios.findById(99)).thenReturn(Optional.empty());

        assertThrows(SesionNoVigenteException.class, () -> servicio.cerrarSesion(99));
    }

    private Instant haceMinutos(long m) {
        return clock.instant().minusSeconds(m * 60);
    }

    @Test
    void renovarEmiteTokenNuevoParaUnaCuentaHabilitadaConservandoElInicioDeSesion() {
        Instant inicio = haceMinutos(60);
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
        when(jwtService.issue(medico, inicio)).thenReturn(new IssuedToken("nuevo", 900, "MEDICO", "Ana Pérez"));

        assertEquals("nuevo", servicio.renovar(7, inicio).value());
    }

    @Test
    void renovarRechazaUnaCuentaDesactivadaOSinPermiso() {
        medico.setActivo(false);
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
        assertThrows(SesionNoVigenteException.class, () -> servicio.renovar(7, haceMinutos(5)));

        medico.setActivo(true);
        medico.revocar(Permisos.ACCESO_ESTUDIOS);
        assertThrows(SesionNoVigenteException.class, () -> servicio.renovar(7, haceMinutos(5)));
        verify(jwtService, never()).issue(any(), any());
    }

    @Test
    void renovarRechazaCuandoSePasoElTopeAbsolutoDeLaSesion() {
        // Tope de 8 horas: a las 8 h exactas ya no se renueva; 7 h 59 min todavía sí.
        assertThrows(SesionNoVigenteException.class, () -> servicio.renovar(7, haceMinutos(8 * 60)));
        assertThrows(SesionNoVigenteException.class, () -> servicio.renovar(7, haceMinutos(20 * 60)));
        verify(usuarios, never()).findById(any());

        Instant casi = haceMinutos(8 * 60 - 1);
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
        when(jwtService.issue(medico, casi)).thenReturn(new IssuedToken("nuevo", 900, "MEDICO", "Ana Pérez"));
        assertEquals("nuevo", servicio.renovar(7, casi).value());
    }

    // ---- revocación de un token concreto ----

    @Test
    void cerrarEstaSesionRevocaSoloEseTokenYNoTocaLaVersionDeSesion() {
        Instant vence = clock.instant().plusSeconds(600);
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
        int version = medico.getSesionVersion();

        servicio.cerrarEstaSesion(7, "jti-1", vence);

        verify(revocaciones).revocar("jti-1", 7, vence);
        assertEquals(version, medico.getSesionVersion());
        verify(cache, never()).invalidarTrasCommit(any(Integer.class));
        verify(auditoria).registrar(TipoEvento.CIERRE_SESION, ResultadoEvento.EXITO, medico, null, "solo este dispositivo");
    }

    // ---- cambio de clave ----

    private void cuentaConClave(String clave) {
        medico = Usuario.nuevo("medico.demo", encoder.encode(clave), "Ana", "Pérez",
                "ana@example.com", new Rol("MEDICO"));
        medico.otorgar(new Permiso(Permisos.ACCESO_ESTUDIOS, "Acceso a estudios"));
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
    }

    @Test
    void cambiarClaveGuardaElHashNuevoCierraLasDemasSesionesYDevuelveTokenNuevo() {
        cuentaConClave(CLAVE);
        medico.setIntentosFallidos(2);
        int version = medico.getSesionVersion();
        when(jwtService.issue(medico)).thenReturn(new IssuedToken("nuevo", 900, "MEDICO", "Ana Pérez"));

        IssuedToken token = servicio.cambiarClave(7, CLAVE, "otra-clave-larga-456");

        assertEquals("nuevo", token.value());
        assertTrue(encoder.matches("otra-clave-larga-456", medico.getPassword()));
        assertFalse(encoder.matches(CLAVE, medico.getPassword()));
        assertEquals(version + 1, medico.getSesionVersion());
        assertEquals(0, medico.getIntentosFallidos());
        assertFalse(medico.isDebeCambiarClave());
        verify(usuarios).save(medico);
        verify(cache).invalidarTrasCommit(7);
        verify(auditoria).registrar(TipoEvento.CLAVE_CAMBIADA, ResultadoEvento.EXITO, medico, null, null);
    }

    @Test
    void cambiarClaveConLaActualIncorrectaSumaUnIntentoYNoCambiaNada() {
        cuentaConClave(CLAVE);
        String hash = medico.getPassword();

        assertThrows(ClaveActualIncorrectaException.class,
                () -> servicio.cambiarClave(7, "no-es-la-clave", "otra-clave-larga-456"));

        assertEquals(hash, medico.getPassword());
        assertEquals(1, medico.getIntentosFallidos());
        verify(auditoria).registrar(TipoEvento.CLAVE_CAMBIADA, ResultadoEvento.FALLO, medico, null, "clave actual incorrecta");
        verify(jwtService, never()).issue(any());
    }

    @Test
    void cambiarClaveRepetidamenteConLaActualIncorrectaBloqueaLaCuenta() {
        cuentaConClave(CLAVE);
        for (int i = 0; i < 3; i++) {
            assertThrows(ClaveActualIncorrectaException.class,
                    () -> servicio.cambiarClave(7, "no-es-la-clave", "otra-clave-larga-456"));
        }
        assertTrue(medico.estaBloqueado(ahora()));
        assertThrows(AccountLockedException.class, () -> servicio.cambiarClave(7, CLAVE, "otra-clave-larga-456"));
    }

    @Test
    void cambiarClaveRechazaUnaClaveNuevaCortaOIgualALaActual() {
        cuentaConClave(CLAVE);
        String hash = medico.getPassword();

        assertThrows(UsuarioInvalidoException.class, () -> servicio.cambiarClave(7, CLAVE, "corta"));
        assertThrows(UsuarioInvalidoException.class, () -> servicio.cambiarClave(7, CLAVE, CLAVE));

        assertEquals(hash, medico.getPassword());
        verify(usuarios, never()).save(any());
    }

    @Test
    void cambiarClaveDeUnaCuentaInexistenteDaSesionNoVigente() {
        when(usuarios.findById(99)).thenReturn(Optional.empty());
        assertThrows(SesionNoVigenteException.class, () -> servicio.cambiarClave(99, CLAVE, "otra-clave-larga-456"));
    }

    @Test
    void despuesDeUnRestablecimientoLaCuentaExigeCambiarLaClaveHastaQueLaCambie() {
        cuentaConClave(CLAVE);
        medico.restablecerClave(encoder.encode("Temporal-12345678"));
        assertTrue(medico.isDebeCambiarClave());
        when(jwtService.issue(medico)).thenReturn(new IssuedToken("nuevo", 900, "MEDICO", "Ana Pérez"));

        servicio.cambiarClave(7, "Temporal-12345678", "definitiva-segura-789");

        assertFalse(medico.isDebeCambiarClave());
    }

    @Test
    void perfilDevuelveRolYPermisosOrdenados() {
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));

        PerfilResponse perfil = servicio.perfil(7);

        assertEquals("medico.demo", perfil.username());
        assertEquals("Ana Pérez", perfil.nombre());
        assertEquals("MEDICO", perfil.rol());
        assertEquals(List.of("ACCESO_ESTUDIOS"), perfil.permisos());
    }
}
