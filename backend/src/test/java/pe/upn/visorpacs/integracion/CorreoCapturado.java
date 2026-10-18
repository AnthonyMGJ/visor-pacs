package pe.upn.visorpacs.integracion;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.upn.visorpacs.recuperacion.EnviadorCorreo;

/**
 * Sustituye al envío SMTP en las pruebas de integración: guarda los correos en memoria en lugar de enviarlos.
 * (Con un servidor SMTP real —Mailpit— se prueba a mano; ver docs.) Es síncrono para que las pruebas sean deterministas.
 */
@Component
@Primary
public class CorreoCapturado implements EnviadorCorreo {

    public record Correo(String para, String asunto, String texto) {}

    private final List<Correo> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String para, String asunto, String texto) {
        enviados.add(new Correo(para, asunto, texto));
    }

    public List<Correo> para(String destinatario) {
        return enviados.stream().filter(c -> c.para().equalsIgnoreCase(destinatario)).toList();
    }
}
