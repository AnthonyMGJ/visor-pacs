package pe.upn.visorpacs.sesion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SesionTokenValidatorTest {

    private final EstadoCuentaCache cache = mock(EstadoCuentaCache.class);
    private final RevocacionTokens revocaciones = mock(RevocacionTokens.class);
    private final SesionTokenValidator validador = new SesionTokenValidator(cache, revocaciones);

    private static Jwt token(Object uid, Object ver) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("medico.demo")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900));
        if (uid != null) b.claim("uid", uid);
        if (ver != null) b.claim("ver", ver);
        return b.build();
    }

    private static EstadoCuenta estado(boolean activo, int version) {
        return new EstadoCuenta(7, activo, version, "MEDICO", activo, Set.of("ACCESO_ESTUDIOS"));
    }

    @Test
    void aceptaUnTokenDeCuentaActivaConLaVersionVigente() {
        when(cache.obtener(7)).thenReturn(Optional.of(estado(true, 2)));
        assertFalse(validador.validate(token(7L, 2L)).hasErrors());
    }

    @Test
    void rechazaCuandoLaCuentaFueDesactivada() {
        when(cache.obtener(7)).thenReturn(Optional.of(estado(false, 2)));
        assertTrue(validador.validate(token(7, 2)).hasErrors());
    }

    @Test
    void rechazaUnTokenConVersionAnteriorTrasCerrarSesion() {
        when(cache.obtener(7)).thenReturn(Optional.of(estado(true, 3)));
        assertTrue(validador.validate(token(7, 2)).hasErrors());
    }

    @Test
    void rechazaUnTokenRevocadoIndividualmenteAunqueLaCuentaYLaVersionSeanValidas() {
        when(cache.obtener(7)).thenReturn(Optional.of(estado(true, 2)));
        Jwt revocado = Jwt.withTokenValue("t").header("alg", "HS256").subject("medico.demo").jti("jti-revocado")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("uid", 7).claim("ver", 2).build();
        when(revocaciones.estaRevocado("jti-revocado")).thenReturn(true);

        assertTrue(validador.validate(revocado).hasErrors());
        // otro token de la misma cuenta, no revocado, sigue valiendo
        assertFalse(validador.validate(token(7, 2)).hasErrors());
    }

    @Test
    void rechazaCuandoLaCuentaYaNoExiste() {
        when(cache.obtener(7)).thenReturn(Optional.empty());
        assertTrue(validador.validate(token(7, 0)).hasErrors());
    }

    @Test
    void rechazaTokensSinIdOSinVersionSinConsultarLaBase() {
        assertTrue(validador.validate(token(null, 0)).hasErrors());
        assertTrue(validador.validate(token(7, null)).hasErrors());
        assertTrue(validador.validate(token("7", "0")).hasErrors());
        verify(cache, never()).obtener(7);
    }
}
