package pe.upn.visorpacs.recuperacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Recuperación de clave", description = "RF-09. Apagado por defecto (404) hasta activar app.recuperacion.habilitada")
public class RecuperacionController {

    static final String MENSAJE_GENERICO =
            "Si el correo está registrado, te enviaremos las instrucciones en unos minutos. Revisa también tu carpeta de spam.";

    private final RecuperacionClaveService servicio;

    public RecuperacionController(RecuperacionClaveService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/olvide-clave")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicita el enlace para restablecer la clave",
            description = "Responde siempre igual (202) exista o no el correo, para no revelar qué cuentas existen. "
                    + "429: demasiadas solicitudes desde la IP. 404: función desactivada.")
    public Map<String, String> olvide(@Valid @RequestBody OlvideClaveRequest request, HttpServletRequest http) {
        servicio.solicitar(request.email().trim(), http.getRemoteAddr());
        return Map.of("mensaje", MENSAJE_GENERICO);
    }

    @PostMapping("/restablecer-clave")
    @Operation(summary = "Fija la clave nueva con el token recibido por correo",
            description = "400: enlace inválido/vencido o clave que no cumple la política. Cierra todas las sesiones de la cuenta; "
                    + "no inicia sesión (hay que entrar con la clave nueva).")
    public Map<String, String> restablecer(@Valid @RequestBody RestablecerClaveRequest request, HttpServletRequest http) {
        servicio.restablecer(request.token().trim(), request.claveNueva(), http.getRemoteAddr());
        return Map.of("mensaje", "Tu clave fue cambiada. Inicia sesión con la clave nueva.");
    }
}
