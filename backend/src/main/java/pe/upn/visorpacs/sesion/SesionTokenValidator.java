package pe.upn.visorpacs.sesion;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Se ejecuta en CADA petición con token (HU12, CA12.2 y CA08.2): además de firma, emisor y vencimiento,
 * exige que la cuenta siga existiendo, esté activa y que la versión de sesión del token sea la vigente.
 * Así, desactivar a un médico o cerrar sesión surte efecto en la siguiente solicitud, no al vencer el token.
 */
@Component
public class SesionTokenValidator implements OAuth2TokenValidator<Jwt> {

    public static final String CLAIM_USUARIO = "uid";
    public static final String CLAIM_VERSION = "ver";
    /** Segundos epoch del inicio de sesión con clave; se conserva al renovar (tope absoluto). */
    public static final String CLAIM_INICIO_SESION = "ses";
    /** true cuando la cuenta tiene una clave temporal y debe cambiarla. */
    public static final String CLAIM_CLAVE_TEMPORAL = "cc";

    private static final OAuth2Error SESION_REVOCADA =
            new OAuth2Error("invalid_token", "Sesión no vigente", null);

    private final EstadoCuentaCache cache;
    private final RevocacionTokens revocaciones;

    public SesionTokenValidator(EstadoCuentaCache cache, RevocacionTokens revocaciones) {
        this.cache = cache;
        this.revocaciones = revocaciones;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object uid = jwt.getClaims().get(CLAIM_USUARIO);
        Object ver = jwt.getClaims().get(CLAIM_VERSION);
        if (!(uid instanceof Number idUsuario) || !(ver instanceof Number version)) {
            return OAuth2TokenValidatorResult.failure(SESION_REVOCADA); // token sin datos de sesión
        }
        if (revocaciones.estaRevocado(jwt.getId())) {
            return OAuth2TokenValidatorResult.failure(SESION_REVOCADA); // este token se cerró individualmente
        }
        boolean vigente = cache.obtener(idUsuario.intValue())
                .filter(c -> c.activo() && c.sesionVersion() == version.intValue())
                .isPresent();
        return vigente ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(SESION_REVOCADA);
    }
}
