package pe.upn.visorpacs.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.upn.visorpacs.domain.Paciente;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Permisos;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.PacienteRepository;
import pe.upn.visorpacs.repository.PermisoRepository;
import pe.upn.visorpacs.repository.RolRepository;
import pe.upn.visorpacs.repository.UsuarioRepository;

class UsuarioServiceTest {

    private static final String CLAVE = "una-clave-larga-123";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final PacienteRepository pacientes = mock(PacienteRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final PermisoRepository permisos = mock(PermisoRepository.class);
    private final UsuarioService servicio = new UsuarioService(usuarios, roles, pacientes, encoder, permisos);

    @BeforeEach
    void preparar() {
        when(roles.findByNombre("MEDICO")).thenReturn(Optional.of(new Rol("MEDICO")));
        when(roles.findByNombre("PACIENTE")).thenReturn(Optional.of(new Rol("PACIENTE")));
        when(roles.findByNombre("ADMIN")).thenReturn(Optional.of(new Rol("ADMIN")));
        when(permisos.findByCodigo(Permisos.ACCESO_ESTUDIOS))
                .thenReturn(Optional.of(new Permiso(Permisos.ACCESO_ESTUDIOS, "Acceso a estudios")));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(pacientes.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void crearMedicoGuardaClaveCifradaYRol() {
        Usuario u = servicio.crearMedico("ana.perez", CLAVE, "Ana", "Pérez", "ana@clinica.pe");
        assertEquals("MEDICO", u.getRol().getNombre());
        assertNotEquals(CLAVE, u.getPassword());
        assertTrue(encoder.matches(CLAVE, u.getPassword()));
        assertNull(u.getPaciente());
    }

    @Test
    void rechazaClaveCortaYUsuarioInvalido() {
        assertThrows(UsuarioInvalidoException.class,
                () -> servicio.crearMedico("ana.perez", "corta", "Ana", "Pérez", "ana@clinica.pe"));
        assertThrows(UsuarioInvalidoException.class,
                () -> servicio.crearMedico("a b", CLAVE, "Ana", "Pérez", "ana@clinica.pe"));
        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    void rechazaUsuarioYCorreoRepetidos() {
        when(usuarios.findByUsername("ana.perez"))
                .thenReturn(Optional.of(Usuario.nuevo("ana.perez", "h", "Ana", "P", "x@y.pe", new Rol("MEDICO"))));
        assertThrows(UsuarioInvalidoException.class,
                () -> servicio.crearMedico("ana.perez", CLAVE, "Ana", "Pérez", "ana@clinica.pe"));
        when(usuarios.existsByEmail("otra@clinica.pe")).thenReturn(true);
        assertThrows(UsuarioInvalidoException.class,
                () -> servicio.crearMedico("otra.cuenta", CLAVE, "Otra", "Cuenta", "otra@clinica.pe"));
    }

    @Test
    void crearPacienteReutilizaElPacienteExistente() {
        Paciente existente = Paciente.nuevo("PRUEBA-0001", "Paciente Uno");
        when(pacientes.findByDicomPacienteId("PRUEBA-0001")).thenReturn(Optional.of(existente));
        Usuario u = servicio.crearPaciente("juan.diaz", CLAVE, "Juan", "Díaz", "juan@correo.pe", "PRUEBA-0001");
        assertEquals("PACIENTE", u.getRol().getNombre());
        assertSame(existente, u.getPaciente());
        verify(pacientes, never()).save(any(Paciente.class));
    }

    @Test
    void crearPacienteNuevoGuardaSuRegistroYExigeElPatientId() {
        Usuario u = servicio.crearPaciente("juan.diaz", CLAVE, "Juan", "Díaz", "juan@correo.pe", "PRUEBA-0009");
        assertEquals("PRUEBA-0009", u.getPaciente().getDicomPacienteId());
        assertThrows(UsuarioInvalidoException.class,
                () -> servicio.crearPaciente("otro.pac", CLAVE, "Otro", "Pac", "otro@correo.pe", " "));
    }

    @Test
    void elMedicoNuevoNaceConElPermisoDeAccesoAEstudios() {
        Usuario u = servicio.crearMedico("ana.perez", CLAVE, "Ana", "Pérez", "ana@clinica.pe");
        assertTrue(u.tienePermiso(Permisos.ACCESO_ESTUDIOS));
        assertTrue(u.habilitada());
    }

    @Test
    void pacienteYAdministradorNoRecibenElPermisoDeEstudios() {
        Usuario paciente = servicio.crearPaciente("juan.diaz", CLAVE, "Juan", "Díaz", "juan@correo.pe", "PRUEBA-0009");
        Usuario admin = servicio.crearAdmin("admin.ti", CLAVE, "Admin", "TI", "admin@clinica.pe");
        assertFalse(paciente.tienePermiso(Permisos.ACCESO_ESTUDIOS));
        assertFalse(admin.tienePermiso(Permisos.ACCESO_ESTUDIOS));
        assertEquals("ADMIN", admin.getRol().getNombre());
        assertTrue(admin.habilitada());
    }

    @Test
    void unPatientIdEnviadoParaUnMedicoSeIgnora() {
        Usuario u = servicio.crearCuenta("MEDICO", "ana.perez", CLAVE, "Ana", "Pérez", "ana@clinica.pe", "PRUEBA-0001");
        assertNull(u.getPaciente());
        verify(pacientes, never()).save(any(Paciente.class));
    }

    @Test
    void usuarioOCorreoRepetidosSeInformanComoDuplicado() {
        when(usuarios.existsByEmail("otra@clinica.pe")).thenReturn(true);
        assertThrows(UsuarioDuplicadoException.class,
                () -> servicio.crearMedico("otra.cuenta", CLAVE, "Otra", "Cuenta", "otra@clinica.pe"));
    }

    @Test
    void faltaElPermisoEnLaBaseEsUnErrorDeConfiguracionNoDeUsuario() {
        when(permisos.findByCodigo(Permisos.ACCESO_ESTUDIOS)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> servicio.crearMedico("ana.perez", CLAVE, "Ana", "Pérez", "ana@clinica.pe"));
    }

    @Test
    void restablecerClaveDevuelveUnaTemporalFuerteLaGuardaCifradaYExigeCambiarla() {
        Usuario u = Usuario.nuevo("ana.perez", encoder.encode(CLAVE), "Ana", "Pérez", "ana@clinica.pe", new Rol("MEDICO"));
        u.setIntentosFallidos(3);
        int version = u.getSesionVersion();

        String temporal = servicio.restablecerClave(u);

        assertEquals(16, temporal.length());
        assertTrue(temporal.matches("[A-Za-z0-9]+"));
        assertTrue(temporal.length() >= UsuarioService.CLAVE_MINIMA);
        assertNotEquals(temporal, u.getPassword());
        assertTrue(encoder.matches(temporal, u.getPassword()));
        assertFalse(encoder.matches(CLAVE, u.getPassword()));
        assertTrue(u.isDebeCambiarClave());
        assertEquals(0, u.getIntentosFallidos());
        assertEquals(version + 1, u.getSesionVersion());
        verify(usuarios).save(u);
    }

    @Test
    void dosRestablecimientosNoRepitenLaClaveTemporal() {
        Usuario u = Usuario.nuevo("ana.perez", "hash", "Ana", "Pérez", "ana@clinica.pe", new Rol("MEDICO"));
        assertNotEquals(servicio.restablecerClave(u), servicio.restablecerClave(u));
    }
}
