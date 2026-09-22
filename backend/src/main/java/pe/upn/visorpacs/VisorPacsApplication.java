package pe.upn.visorpacs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VisorPacsApplication {
    public static void main(String[] args) {
        SpringApplication.run(VisorPacsApplication.class, args);
    }
}
