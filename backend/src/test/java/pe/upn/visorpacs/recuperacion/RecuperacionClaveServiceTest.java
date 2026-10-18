package pe.upn.visorpacs.recuperacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import pe.upn.visorpacs.auditoria.AuditoriaService;
import pe.upn.visorpacs.auditoria.ResultadoEvento;
import pe.upn.visorpacs.auditoria.TipoEvento;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Permisos;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.UsuarioRepository;
import pe.upn.visorpacs.sesion.EstadoCuentaCache;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;
import pe.upn.visorpacs.web.RecursoNoEncontradoException;

/** RF-09 (lado servidor), sin base de datos ni red: almacén y correo son dobles en memoria. */
class RecuperacionClaveServiceTest {

    private static final String CLAVE_VIEJA = "ClaveVieja-12345";
    private static final String CLAVE_NUEVA = "ClaveNueva-67890";

    /** Reloj que se puede adelantar para probar el vencimiento. */
    static class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-10-17T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
    }

    /** Almacén en memoria con la misma semántica que el JDBC (un solo uso, vencimiento, anulación). */
    static class AlmacenMemoria implements AlmacenRecuperacion {
        record Fila(int idUsuario, String hash, Instant creado, Instant vence, Instant usado) {}
        final List<Fila> filas = new ArrayList<>();

        @Override public void guardar(int id, String hash, Instant creado, Instant vence, String ip) {
            filas.add(new Fila(id, hash, creado, vence, null));
        }
        @Override public int contarDesde(int id, Instant desde) {
            return (int) filas.stream().filter(f -> f.idUsuario() == id && !f.creado().isBefore(desde)).count();
        }
        @Override public Optional<Registro> buscar(String hash) {
            return filas.stream().filter(f -> f.hash().equals(hash)).findFirst()
                    .map(f -> new Registro(f.idUsuario(), f.vence(), f.usado()));
        }
        @Override public boolean consumir(String hash, Instant ahora) {
            for (int i = 0; i < filas.size(); i++) {
                Fila f = filas.get(i);
                if (f.hash().equals(hash) && f.usado() == null && f.vence().isAfter(ahora)) {
                    filas.set(i, new Fila(f.idUsuario(), f.hash(), f.creado(), f.vence(), ahora));
                    return true;
                }
            }
            return false;
        }
        @Override public int anularPendientes(int id, Instant ahora) {
            int n = 0;
            for (int i = 0; i < filas.size(); i++) {
                Fila f = filas.get(i);
                if (f.idUsuario() == id && f.usado() == null) {
                    filas.set(i, new Fila(f.idUsuario(), f.hash(), f.creado(), f.vence(), ahora));
                    n++;
                }
            }
            return n;
        }
        @Override public void purgarAnteriores(Instant limite) {
            filas.removeIf(f -> f.vence().isBefore(limite));
        }
    }

    record Correo(String para, String asunto, String texto) {}

    private final RelojMovil reloj = new RelojMovil();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final EstadoCuentaCache cache = mock(EstadoCuentaCache.class);
    private final AlmacenMemoria almacen = new AlmacenMemoria();
    private final List<Correo> enviados = new ArrayList<>();
    private final Map<Integer, Usuario> porId = new HashMap<>();
    private RecuperacionClaveService servicio;
    private Usuario medico;
    private Usuario paciente;
    private Usuario admin;

    private Usuario cuenta(int id, String username, String rol, String email) {
        Usuario u = Usuario.nuevo(username, encoder.encode(CLAVE_VIEJA), "Ana", "Pérez", email, new Rol(rol));
        ReflectionTestUtils.setField(u, "id", id);
        porId.put(id, u);
        when(usuarios.findByEmail(email)).thenReturn(Optional.of(u));
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private RecuperacionClaveService crear(boolean habilitada) {
        RecuperacionProperties props = new RecuperacionProperties(habilitada, 15, 3, 10, 3600,
                "http://app.test/", "no-reply@test");
        EnviadorCorreo correo = (para, asunto, texto) -> enviados.add(new Correo(para, asunto, texto));
        return new RecuperacionClaveService(usuarios, almacen, encoder, auditoria, cache, correo, props, reloj);
    }

    @BeforeEach
    void preparar() {
        servicio = crear(true);
        medico = cuenta(1, "medico.demo", "MEDICO", "medico@clinica.pe");
        medico.otorgar(new Permiso(Permisos.ACCESO_ESTUDIOS, "Acceso a estudios"));
        paciente = cuenta(2, "paciente.demo", "PACIENTE", "paciente@correo.pe");
        admin = cuenta(3, "admin.demo", "ADMIN", "admin@clinica.pe");
        when(usuarios.findByEmail("nadie@correo.pe")).thenReturn(Optional.empty());
        when(usuarios.findByRolNombreAndActivoTrue("ADMIN")).thenReturn(List.of(admin));
    }

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private String tokenDelUltimoCorreo() {
        Matcher m = TOKEN.matcher(enviados.get(enviados.size() - 1).texto());
        assertTrue(m.find(), "el correo debe traer el enlace con #token=");
        return m.group(1);
    }

    // ---- interruptor ----

    @Test
    void conLaFuncionApagadaAmbasOperacionesResponden404() {
        RecuperacionClaveService apagado = crear(false);
        assertThrows(RecursoNoEncontradoException.class, () -> apagado.solicitar("medico@clinica.pe", "1.1.1.1"));
        assertThrows(RecursoNoEncontradoException.class, () -> apagado.restablecer("x", CLAVE_NUEVA, "1.1.1.1"));
        assertTrue(enviados.isEmpty());
    }

    // ---- solicitud ----

    @Test
    void medicoActivoRecibeUnEnlaceConTokenEnElFragmentoYVigenciaDeQuinceMinutos() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");

        assertEquals(1, enviados.size());
        Correo c = enviados.get(0);
        assertEquals("medico@clinica.pe", c.para());
        assertTrue(c.texto().contains("http://app.test/nueva-clave#token="), "base sin barra final + fragmento");
        assertFalse(c.asunto().contains("token"), "el asunto no lleva el token");
        assertEquals(1, almacen.filas.size());
        assertEquals(Duration.ofMinutes(15), Duration.between(almacen.filas.get(0).creado(), almacen.filas.get(0).vence()));
        verify(auditoria).registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.EXITO, medico, null, null);
    }

    @Test
    void soloSeGuardaElHashDelToken() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");

        String token = tokenDelUltimoCorreo();
        String guardado = almacen.filas.get(0).hash();
        assertNotEquals(token, guardado);
        assertEquals(64, guardado.length());
        assertEquals(RecuperacionClaveService.hash(token), guardado);
        assertTrue(token.length() >= 43, "256 bits en Base64 URL = 43 caracteres");
    }

    @Test
    void dosSolicitudesGeneranTokensDistintos() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String a = tokenDelUltimoCorreo();
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        assertNotEquals(a, tokenDelUltimoCorreo());
    }

    @Test
    void pacienteTambienPuedeRecuperar() {
        servicio.solicitar("paciente@correo.pe", "1.1.1.1");
        assertEquals(1, enviados.size());
    }

    @Test
    void correoNoRegistradoNoEnviaNadaNoGuardaNadaYNoLanzaError() {
        servicio.solicitar("nadie@correo.pe", "1.1.1.1");

        assertTrue(enviados.isEmpty());
        assertTrue(almacen.filas.isEmpty());
        verify(auditoria).registrar(eq(TipoEvento.RECUPERACION_SOLICITADA), eq(ResultadoEvento.FALLO),
                isNull(), isNull(), isNull(), isNull(), eq("correo no registrado: n***@correo.pe"));
    }

    @Test
    void adminQuedaExcluido() {
        servicio.solicitar("admin@clinica.pe", "1.1.1.1");
        assertTrue(enviados.isEmpty());
        assertTrue(almacen.filas.isEmpty());
        verify(auditoria).registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.FALLO, admin, null, "rol ADMIN excluido");
    }

    @Test
    void cuentaInactivaNoRecibeCorreo() {
        paciente.setActivo(false);
        servicio.solicitar("paciente@correo.pe", "1.1.1.1");
        assertTrue(enviados.isEmpty());
        verify(auditoria).registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.FALLO, paciente, null, "cuenta inactiva");
    }

    @Test
    void medicoSinPermisoDeAccesoNoRecibeCorreo() {
        Usuario sinPermiso = cuenta(4, "medico.sin", "MEDICO", "sin@clinica.pe");
        servicio.solicitar("sin@clinica.pe", "1.1.1.1");
        assertTrue(enviados.isEmpty());
        verify(auditoria).registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.FALLO, sinPermiso, null, "sin permiso de acceso");
    }

    @Test
    void laCuartaSolicitudEnUnaHoraNoEnviaCorreoPeroNoDaError() {
        for (int i = 0; i < 4; i++) {
            servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        }
        assertEquals(3, enviados.size());
        verify(auditoria).registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.FALLO, medico, null, "límite por cuenta superado");
    }

    @Test
    void pasadaUnaHoraLaCuentaVuelveAPoderSolicitar() {
        for (int i = 0; i < 3; i++) {
            servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        }
        reloj.avanzar(Duration.ofMinutes(61));
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        assertEquals(4, enviados.size());
    }

    @Test
    void unaSolicitudNuevaAnulaElEnlaceAnterior() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String viejo = tokenDelUltimoCorreo();
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String nuevo = tokenDelUltimoCorreo();

        assertThrows(TokenRecuperacionInvalidoException.class, () -> servicio.restablecer(viejo, CLAVE_NUEVA, "1.1.1.1"));
        servicio.restablecer(nuevo, CLAVE_NUEVA, "1.1.1.1");
    }

    // ---- restablecer ----

    @Test
    void restablecerCambiaLaClaveCierraSesionesYAvisa() {
        medico.registrarIntentoFallido(1, Duration.ofMinutes(15), LocalDateTime.of(2026, 10, 17, 12, 0)); // la bloquea
        assertTrue(medico.estaBloqueado(LocalDateTime.of(2026, 10, 17, 12, 1)));
        int version = medico.getSesionVersion();
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        enviados.clear();

        servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1");

        assertTrue(encoder.matches(CLAVE_NUEVA, medico.getPassword()));
        assertFalse(encoder.matches(CLAVE_VIEJA, medico.getPassword()));
        assertEquals(version + 1, medico.getSesionVersion(), "sube la versión de sesión: todas las sesiones se cierran");
        assertFalse(medico.estaBloqueado(LocalDateTime.of(2026, 10, 17, 12, 1)), "levanta el bloqueo por intentos");
        assertFalse(medico.isDebeCambiarClave());
        verify(usuarios).save(medico);
        verify(cache).invalidarTrasCommit(1);
        verify(auditoria).registrar(TipoEvento.RECUPERACION_COMPLETADA, ResultadoEvento.EXITO, medico, null, null);
        // correo de "tu clave fue cambiada" al médico + aviso al administrador
        assertEquals(2, enviados.size());
        assertEquals("medico@clinica.pe", enviados.get(0).para());
        assertEquals("admin@clinica.pe", enviados.get(1).para());
        assertFalse(enviados.get(0).texto().contains(CLAVE_NUEVA));
        assertFalse(enviados.get(1).texto().contains(CLAVE_NUEVA));
    }

    @Test
    void cuandoRecuperaUnPacienteNoSeAvisaAlAdministrador() {
        servicio.solicitar("paciente@correo.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        enviados.clear();

        servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1");

        assertEquals(1, enviados.size());
        assertEquals("paciente@correo.pe", enviados.get(0).para());
    }

    @Test
    void elEnlaceSoloSirveUnaVez() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1");

        assertThrows(TokenRecuperacionInvalidoException.class,
                () -> servicio.restablecer(token, "OtraClave-123456", "1.1.1.1"));
        assertTrue(encoder.matches(CLAVE_NUEVA, medico.getPassword()), "el segundo intento no cambia nada");
    }

    @Test
    void elEnlaceVenceALosQuinceMinutos() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        reloj.avanzar(Duration.ofMinutes(15));

        assertThrows(TokenRecuperacionInvalidoException.class, () -> servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1"));
        assertTrue(encoder.matches(CLAVE_VIEJA, medico.getPassword()));
    }

    @Test
    void justoAntesDelVencimientoSigueValiendo() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        reloj.avanzar(Duration.ofMinutes(14).plusSeconds(59));

        servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1");
        assertTrue(encoder.matches(CLAVE_NUEVA, medico.getPassword()));
    }

    @Test
    void unTokenInventadoSeRechazaYQuedaEnLaAuditoriaSinElToken() {
        assertThrows(TokenRecuperacionInvalidoException.class,
                () -> servicio.restablecer("token-que-no-existe", CLAVE_NUEVA, "1.1.1.1"));
        verify(auditoria).registrar(TipoEvento.RECUPERACION_FALLIDA, ResultadoEvento.FALLO, (Usuario) null, null, "enlace desconocido");
    }

    @Test
    void claveDebilSeRechazaSinGastarElEnlace() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();

        assertThrows(UsuarioInvalidoException.class, () -> servicio.restablecer(token, "corta", "1.1.1.1"));
        assertTrue(encoder.matches(CLAVE_VIEJA, medico.getPassword()));

        servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1"); // con el mismo enlace y una clave válida sí funciona
        assertTrue(encoder.matches(CLAVE_NUEVA, medico.getPassword()));
    }

    @Test
    void laClaveNuevaDebeSerDistintaDeLaActual() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        assertThrows(UsuarioInvalidoException.class, () -> servicio.restablecer(token, CLAVE_VIEJA, "1.1.1.1"));
    }

    @Test
    void siLaCuentaSeDesactivaDespuesDeSolicitarElEnlaceYaNoSirve() {
        servicio.solicitar("paciente@correo.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        paciente.setActivo(false);

        assertThrows(TokenRecuperacionInvalidoException.class, () -> servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1"));
        assertTrue(encoder.matches(CLAVE_VIEJA, paciente.getPassword()));
        verify(usuarios, never()).save(any());
    }

    @Test
    void nuncaSeAuditaNiElTokenNiLaClave() {
        servicio.solicitar("medico@clinica.pe", "1.1.1.1");
        String token = tokenDelUltimoCorreo();
        servicio.restablecer(token, CLAVE_NUEVA, "1.1.1.1");
        // Ningún detalle de auditoría contiene el token ni la clave: se revisa todo lo que recibió el mock.
        org.mockito.Mockito.mockingDetails(auditoria).getInvocations().forEach(inv ->
                java.util.Arrays.stream(inv.getArguments()).forEach(a -> {
                    if (a instanceof String s) {
                        assertFalse(s.contains(token) || s.contains(CLAVE_NUEVA), "auditoría con dato secreto: " + s);
                    }
                }));
    }

    @Test
    void enmascararDejaSoloLaInicialYElDominio() {
        assertEquals("a***@dominio.pe", RecuperacionClaveService.enmascarar("ana.perez@dominio.pe"));
        assertEquals("***", RecuperacionClaveService.enmascarar("sin-arroba"));
        assertEquals("***", RecuperacionClaveService.enmascarar("@x.pe"));
    }
}
