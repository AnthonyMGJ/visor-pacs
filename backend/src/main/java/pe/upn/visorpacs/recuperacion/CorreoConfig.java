package pe.upn.visorpacs.recuperacion;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CorreoConfig {

    /** Pocos hilos y cola acotada: un SMTP caído no puede acumular hilos ni memoria sin límite. */
    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskExecutor correoExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("correo-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // el envío lo captura y lo registra
        return ex;
    }

    @Bean
    EnviadorCorreo enviadorCorreo(JavaMailSender sender, RecuperacionProperties props, ThreadPoolTaskExecutor correoExecutor) {
        return new EnviadorCorreoAsincrono(new EnviadorCorreoSmtp(sender, props.remitente()), correoExecutor);
    }
}
