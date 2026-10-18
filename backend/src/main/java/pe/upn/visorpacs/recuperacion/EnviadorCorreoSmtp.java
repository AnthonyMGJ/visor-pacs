package pe.upn.visorpacs.recuperacion;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Envío real por SMTP (bloqueante; los tiempos de espera se fijan en spring.mail.properties.*). */
public class EnviadorCorreoSmtp implements EnviadorCorreo {

    private final JavaMailSender sender;
    private final String remitente;

    public EnviadorCorreoSmtp(JavaMailSender sender, String remitente) {
        this.sender = sender;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(para);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        sender.send(mensaje);
    }
}
