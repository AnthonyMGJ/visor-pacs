package pe.upn.visorpacs.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.PacienteRepository;
import pe.upn.visorpacs.repository.RolRepository;
import pe.upn.visorpacs.repository.UsuarioRepository;

class UsuarioServiceTest {

    private static final String CLAVE = "una-clave-larga-123";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final PacienteRepository pacientes = mock(PacienteRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UsuarioService servicio = new UsuarioService(usuarios, roles, pacientes, encoder);

    @BeforeEach
    void preparar() {
        when(roles.findByNombre("MEDICO")).thenReturn(Optional.of(new Rol("MEDICO")));
        when(roles.findByNombre("PACIENTE")).thenReturn(Optional.of(new Rol("PACIENTE")));
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
}
