package pe.upn.visorpacs.recuperacion;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrega el envío a otro hilo y vuelve de inmediato: si el servidor SMTP está lento o caído, la solicitud
 * del usuario no espera (RF-09, CA09.12). Un fallo se registra en el log sin dirección ni contenido del
 * mensaje y nunca llega al que llama, para que la respuesta sea idéntica con o sin éxito.
 */
public class EnviadorCorreoAsincrono implements EnviadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(EnviadorCorreoAsincrono.class);

    private final EnviadorCorreo delegado;
    private final Executor ejecutor;

    public EnviadorCorreoAsincrono(EnviadorCorreo delegado, Executor ejecutor) {
        this.delegado = delegado;
        this.ejecutor = ejecutor;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        try {
            ejecutor.execute(() -> {
                try {
                    delegado.enviar(para, asunto, texto);
                } catch (RuntimeException e) {
                    log.error("No se pudo enviar el correo '{}': {}", asunto, e.toString());
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Cola de correo llena; se descartó el envío '{}'", asunto);
        }
    }
}
