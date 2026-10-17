package pe.upn.visorpacs.auditoria;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.upn.visorpacs.domain.Usuario;

/**
 * Punto único para registrar eventos. Es "mejor esfuerzo": si la base falla, se deja constancia en el log
 * de errores y la operación del usuario continúa (la disponibilidad clínica pesa más que un registro perdido).
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private static final int MAX_CLAVES = 10_000;

    private final AuditoriaWriter writer;
    private final Clock clock;
    private final long ventanaMs;
    private final ConcurrentHashMap<String, Long> aperturasRecientes = new ConcurrentHashMap<>();

    public AuditoriaService(AuditoriaWriter writer, Clock clock, AuditoriaProperties props) {
        this.writer = writer;
        this.clock = clock;
        this.ventanaMs = Math.max(0, props.aperturaVentanaMinutos()) * 60_000L;
    }

    public void registrar(TipoEvento tipo, ResultadoEvento resultado, Usuario usuario, String estudio, String detalle) {
        registrar(tipo, resultado, usuario == null ? null : usuario.getId(),
                usuario == null ? null : usuario.getUsername(),
                usuario == null ? null : usuario.getRol().getNombre(), estudio, detalle);
    }

    public void registrar(TipoEvento tipo, ResultadoEvento resultado, Integer idUsuario, String username,
                          String rol, String estudio, String detalle) {
        try {
            writer.guardar(new EventoAuditoria(LocalDateTime.now(clock), tipo, resultado, idUsuario,
                    username, rol, estudio, ipActual(), detalle));
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el evento de auditoría {} ({}): {}", tipo, resultado, e.toString());
        }
    }

    /**
     * Registra la apertura (o el intento denegado) de un estudio, una sola vez por usuario y estudio
     * dentro de la ventana configurada.
     */
    public void registrarAccesoEstudio(TipoEvento tipo, ResultadoEvento resultado, Integer idUsuario,
                                       String username, String rol, String estudio, String detalle) {
        if (primeraVezEnVentana(tipo + "|" + idUsuario + "|" + estudio)) {
            registrar(tipo, resultado, idUsuario, username, rol, estudio, detalle);
        }
    }

    private boolean primeraVezEnVentana(String clave) {
        long ahora = clock.millis();
        if (aperturasRecientes.size() >= MAX_CLAVES) {
            aperturasRecientes.clear();
        }
        boolean[] nueva = {false};
        aperturasRecientes.compute(clave, (k, vence) -> {
            if (vence == null || vence <= ahora) {
                nueva[0] = true;
                return ahora + ventanaMs;
            }
            return vence;
        });
        return nueva[0];
    }

    /** IP de la petición en curso (null fuera de una petición HTTP, p. ej. en el comando de consola). */
    private static String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
