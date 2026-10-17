package pe.upn.visorpacs.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import org.springframework.web.bind.annotation.RequestParam;
import pe.upn.visorpacs.sesion.SesionTokenValidator;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Inicio, renovación y cierre de sesión")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión", description = "Devuelve un JWT de corta duración. 401: credenciales inválidas; 423: cuenta bloqueada temporalmente.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return respuesta(authService.login(request.username().trim(), request.password()));
    }

    /** Un token nuevo para quien sigue activo; la caducidad por inactividad la aplica el vencimiento del token (HU12). */
    @PostMapping("/renovar")
    @Operation(summary = "Renueva el token de una sesión vigente")
    public LoginResponse renovar(@AuthenticationPrincipal Jwt jwt) {
        return respuesta(authService.renovar(idUsuario(jwt), inicioSesion(jwt)));
    }

    /**
     * Cierra la sesión en el servidor. Por defecto revoca todos los tokens de la cuenta (todos los dispositivos);
     * con soloEsta=true revoca únicamente el token que se presenta.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra la sesión y revoca los tokens emitidos",
            description = "Por defecto cierra todas las sesiones de la cuenta; con soloEsta=true, solo el token enviado.")
    public void logout(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "false") boolean soloEsta) {
        if (soloEsta) {
            authService.cerrarEstaSesion(idUsuario(jwt), jwt.getId(), jwt.getExpiresAt());
        } else {
            authService.cerrarSesion(idUsuario(jwt));
        }
    }

    @PostMapping("/cambiar-clave")
    @Operation(summary = "Cambia la clave de la cuenta autenticada",
            description = "Cierra las demás sesiones y devuelve un token nuevo. 400: clave actual incorrecta o clave nueva inválida; 423: cuenta bloqueada. Es lo único (junto con /me y /logout) que permite un token de clave temporal.")
    public LoginResponse cambiarClave(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CambiarClaveRequest request) {
        return respuesta(authService.cambiarClave(idUsuario(jwt), request.claveActual(), request.claveNueva()));
    }

    @GetMapping("/me")
    @Operation(summary = "Datos de la cuenta autenticada")
    public PerfilResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.perfil(idUsuario(jwt));
    }

    private static LoginResponse respuesta(IssuedToken token) {
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), token.rol(), token.nombre(),
                token.debeCambiarClave());
    }

    /** Instante en que la persona escribió su clave; si el token no lo trae, se usa su fecha de emisión. */
    static Instant inicioSesion(Jwt jwt) {
        Object ses = jwt.getClaims().get(SesionTokenValidator.CLAIM_INICIO_SESION);
        if (ses instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        return jwt.getIssuedAt() != null ? jwt.getIssuedAt() : Instant.EPOCH;
    }

    /** El claim "uid" ya fue exigido y verificado por SesionTokenValidator antes de llegar aquí. */
    static int idUsuario(Jwt jwt) {
        return ((Number) jwt.getClaims().get(SesionTokenValidator.CLAIM_USUARIO)).intValue();
    }
}
