package pe.upn.visorpacs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos de la documentación OpenAPI (Swagger UI en /swagger-ui.html, JSON en /v3/api-docs). */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearer-jwt";

    @Bean
    OpenAPI visorPacsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Visor PACS - API")
                        .version("0.1.0")
                        .description("API del visor web de imágenes médicas. Roles: MEDICO, PACIENTE y ADMIN. "
                                + "Inicia sesión en POST /api/auth/login y usa el token con «Authorize»."))
                .components(new Components().addSecuritySchemes(ESQUEMA,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
