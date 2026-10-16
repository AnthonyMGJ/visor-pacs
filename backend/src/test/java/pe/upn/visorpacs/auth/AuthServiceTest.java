package pe.upn.visorpacs.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String CLAVE = "ClaveCorrecta-123";
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), ZoneOffset.UTC);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // rápido para pruebas
    @Mock UsuarioRepository usuarios;
    @Mock JwtService jwtService;
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
        servicio = new AuthService(usuarios, encoder, jwtService, props, clock);
        medico = Usuario.nuevo("medico.demo", encoder.encode(CLAVE), "Ana", "Pérez",
                "ana@example.com", new Rol("MEDICO"));
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
}
