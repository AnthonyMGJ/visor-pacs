package pe.upn.visorpacs.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.upn.visorpacs.auditoria.AuditoriaService;
import pe.upn.visorpacs.auditoria.ResultadoEvento;
import pe.upn.visorpacs.auditoria.TipoEvento;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Permisos;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.PermisoRepository;
import pe.upn.visorpacs.repository.UsuarioRepository;
import pe.upn.visorpacs.sesion.EstadoCuentaCache;
import pe.upn.visorpacs.usuario.UsuarioDuplicadoException;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;
import pe.upn.visorpacs.usuario.UsuarioService;
import pe.upn.visorpacs.web.OperacionNoPermitidaException;
import pe.upn.visorpacs.web.RecursoNoEncontradoException;

class AdminUsuarioServiceTest {

    private static final ActorAdmin ADMIN = new ActorAdmin(1, "admin.demo");
    private static final Permiso ACCESO = new Permiso(Permisos.ACCESO_ESTUDIOS, "Acceso a estudios");

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PermisoRepository permisos = mock(PermisoRepository.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final EstadoCuentaCache cache = mock(EstadoCuentaCache.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
    private final AdminUsuarioService servicio =
            new AdminUsuarioService(usuarios, permisos, usuarioService, cache, auditoria, reloj);

    private Usuario medico;
    private Usuario paciente;

    private static Usuario con(int id, String username, String rol) {
        Usuario u = Usuario.nuevo(username, "hash", "Nom", "Ape", username + "@example.com", new Rol(rol));
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @BeforeEach
    void preparar() {
        medico = con(7, "ana.perez", "MEDICO");
        medico.otorgar(ACCESO);
        paciente = con(8, "juan.diaz", "PACIENTE");
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
        when(usuarios.findById(8)).thenReturn(Optional.of(paciente));
        when(usuarios.findById(404)).thenReturn(Optional.empty());
        when(permisos.findByCodigo(Permisos.ACCESO_ESTUDIOS)).thenReturn(Optional.of(ACCESO));
        when(permisos.findByCodigo("INEXISTENTE")).thenReturn(Optional.empty());
    }

    // ---- crear ----

    private static CrearUsuarioRequest peticion(String rol) {
        return new CrearUsuarioRequest("nuevo.user", "una-clave-larga-123", "Nuevo", "User", "nuevo@clinica.pe", rol, "PRUEBA-0001");
    }

    @Test
    void crearMedicoDelegaEnUsuarioServiceYAudita() {
        Usuario creado = con(9, "nuevo.user", "MEDICO");
        when(usuarioService.crearCuenta(eq("MEDICO"), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(creado);

        UsuarioDto dto = servicio.crear(ADMIN, peticion("medico")); // el rol se normaliza

        assertEquals("nuevo.user", dto.username());
        assertEquals("MEDICO", dto.rol());
        verify(auditoria).registrar(eq(TipoEvento.USUARIO_CREADO), eq(ResultadoEvento.EXITO), eq(1), eq("admin.demo"),
                eq("ADMIN"), any(), anyString());
    }

    @Test
    void noSePuedeCrearUnAdministradorPorLaApi() {
        assertThrows(UsuarioInvalidoException.class, () -> servicio.crear(ADMIN, peticion("ADMIN")));
        assertThrows(UsuarioInvalidoException.class, () -> servicio.crear(ADMIN, peticion("SUPERUSUARIO")));
        verify(usuarioService, never()).crearCuenta(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- editar ----

    @Test
    void editarCambiaDatosYAudita() {
        UsuarioDto dto = servicio.editar(ADMIN, 7, new EditarUsuarioRequest("Ana María", "Pérez Soto", "ana.m@clinica.pe"));

        assertEquals("Ana María", dto.nombres());
        assertEquals("ana.m@clinica.pe", dto.email());
        verify(usuarios).save(medico);
        verify(auditoria).registrar(eq(TipoEvento.USUARIO_EDITADO), eq(ResultadoEvento.EXITO), eq(1), eq("admin.demo"),
                eq("ADMIN"), any(), anyString());
    }

    @Test
    void editarRechazaUnCorreoYaUsadoPorOtraCuenta() {
        when(usuarios.existsByEmailAndIdNot("ocupado@clinica.pe", 7)).thenReturn(true);
        assertThrows(UsuarioDuplicadoException.class,
                () -> servicio.editar(ADMIN, 7, new EditarUsuarioRequest("Ana", "Pérez", "ocupado@clinica.pe")));
        verify(usuarios, never()).save(any());
    }

    @Test
    void editarRechazaUnCorreoMalFormado() {
        assertThrows(UsuarioInvalidoException.class,
                () -> servicio.editar(ADMIN, 7, new EditarUsuarioRequest("Ana", "Pérez", "no-es-correo")));
    }

    @Test
    void editarUnaCuentaInexistenteDa404() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.editar(ADMIN, 404, new EditarUsuarioRequest("Ana", "Pérez", "a@b.pe")));
    }

    // ---- desactivar / activar (HU12, CA12.2) ----

    @Test
    void desactivarRevocaLasSesionesInvalidaLaMemoriaYAudita() {
        int versionAntes = medico.getSesionVersion();

        UsuarioDto dto = servicio.cambiarEstado(ADMIN, 7, false);

        assertFalse(dto.activo());
        assertFalse(medico.isActivo());
        assertEquals(versionAntes + 1, medico.getSesionVersion());
        verify(usuarios).save(medico);
        verify(cache).invalidarTrasCommit(7);
        verify(auditoria).registrar(eq(TipoEvento.USUARIO_DESACTIVADO), eq(ResultadoEvento.EXITO), eq(1),
                eq("admin.demo"), eq("ADMIN"), any(), anyString());
    }

    @Test
    void activarDeNuevoLimpiaBloqueosEIntentos() {
        medico.setActivo(false);
        medico.setIntentosFallidos(3);
        medico.setBloqueadoHasta(java.time.LocalDateTime.of(2030, 1, 1, 0, 0));

        UsuarioDto dto = servicio.cambiarEstado(ADMIN, 7, true);

        assertTrue(dto.activo());
        assertFalse(dto.bloqueado());
        assertEquals(0, medico.getIntentosFallidos());
        verify(auditoria).registrar(eq(TipoEvento.USUARIO_ACTIVADO), eq(ResultadoEvento.EXITO), eq(1),
                eq("admin.demo"), eq("ADMIN"), any(), anyString());
    }

    @Test
    void cambiarAlMismoEstadoNoHaceNadaNiAudita() {
        servicio.cambiarEstado(ADMIN, 7, true);
        verify(usuarios, never()).save(any());
        verify(cache, never()).invalidarTrasCommit(7);
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unAdministradorNoPuedeDesactivarseASiMismo() {
        Usuario yo = con(1, "admin.demo", "ADMIN");
        when(usuarios.findById(1)).thenReturn(Optional.of(yo));

        assertThrows(OperacionNoPermitidaException.class, () -> servicio.cambiarEstado(ADMIN, 1, false));

        assertTrue(yo.isActivo());
        verify(usuarios, never()).save(any());
    }

    // ---- permisos (HU08, CA08.2) ----

    @Test
    void revocarPermisoQuitaElAccesoInvalidaLaMemoriaYAudita() {
        UsuarioDto dto = servicio.revocarPermiso(ADMIN, 7, "acceso_estudios"); // el código se normaliza

        assertTrue(dto.permisos().isEmpty());
        assertFalse(medico.habilitada());
        verify(cache).invalidarTrasCommit(7);
        verify(auditoria).registrar(eq(TipoEvento.PERMISO_REVOCADO), eq(ResultadoEvento.EXITO), eq(1),
                eq("admin.demo"), eq("ADMIN"), any(), anyString());
    }

    @Test
    void otorgarPermisoDevuelveElAccesoYEsIdempotente() {
        medico.revocar(Permisos.ACCESO_ESTUDIOS);

        UsuarioDto primera = servicio.otorgarPermiso(ADMIN, 7, Permisos.ACCESO_ESTUDIOS);
        UsuarioDto segunda = servicio.otorgarPermiso(ADMIN, 7, Permisos.ACCESO_ESTUDIOS);

        assertEquals(List.of("ACCESO_ESTUDIOS"), primera.permisos());
        assertEquals(List.of("ACCESO_ESTUDIOS"), segunda.permisos());
        verify(auditoria, org.mockito.Mockito.times(1)).registrar(eq(TipoEvento.PERMISO_OTORGADO),
                eq(ResultadoEvento.EXITO), eq(1), eq("admin.demo"), eq("ADMIN"), any(), anyString());
    }

    @Test
    void revocarUnPermisoQueNoTieneNoAuditaNada() {
        medico.revocar(Permisos.ACCESO_ESTUDIOS);
        servicio.revocarPermiso(ADMIN, 7, Permisos.ACCESO_ESTUDIOS);
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void permisoInexistenteDa404() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.otorgarPermiso(ADMIN, 7, "INEXISTENTE"));
    }

    @Test
    void losPermisosSoloSeAsignanAMedicos() {
        assertThrows(OperacionNoPermitidaException.class,
                () -> servicio.otorgarPermiso(ADMIN, 8, Permisos.ACCESO_ESTUDIOS));
        assertThrows(OperacionNoPermitidaException.class,
                () -> servicio.revocarPermiso(ADMIN, 8, Permisos.ACCESO_ESTUDIOS));
    }

    @Test
    void obtenerUnaCuentaInexistenteDa404() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(404));
    }

    @Test
    void elDtoNuncaExponeLaClave() {
        UsuarioDto dto = servicio.obtener(7);
        assertFalse(dto.toString().contains("hash"));
    }

    // ---- restablecer clave ----

    @Test
    void restablecerClaveEntregaLaTemporalInvalidaLaMemoriaYAudita() {
        when(usuarioService.restablecerClave(medico)).thenReturn("Temporal23456789");

        ClaveTemporalDto dto = servicio.restablecerClave(ADMIN, 7);

        assertEquals("Temporal23456789", dto.claveTemporal());
        verify(cache).invalidarTrasCommit(7);
        verify(auditoria).registrar(TipoEvento.CLAVE_RESTABLECIDA, ResultadoEvento.EXITO, 1, "admin.demo", "ADMIN",
                null, "cuenta=ana.perez");
    }

    @Test
    void laClaveTemporalNuncaQuedaEnLaAuditoria() {
        when(usuarioService.restablecerClave(medico)).thenReturn("Temporal23456789");

        servicio.restablecerClave(ADMIN, 7);

        verify(auditoria, never()).registrar(any(), any(), any(Integer.class), anyString(), anyString(), any(),
                org.mockito.ArgumentMatchers.contains("Temporal23456789"));
    }

    @Test
    void unAdminNoPuedeRestablecerSuPropiaClaveNiLaDeUnaCuentaInexistente() {
        Usuario yo = con(1, "admin.demo", "ADMIN");
        when(usuarios.findById(1)).thenReturn(Optional.of(yo));

        assertThrows(OperacionNoPermitidaException.class, () -> servicio.restablecerClave(ADMIN, 1));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.restablecerClave(ADMIN, 404));
        verify(usuarioService, never()).restablecerClave(any());
    }
}
