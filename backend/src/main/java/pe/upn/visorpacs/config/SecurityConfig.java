package pe.upn.visorpacs.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
            ErrorSeguridadHandler errores) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // API sin sesión: el token va en el header Authorization
            .cors(c -> c.configurationSource(cors))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                // Documentación de la API (se puede apagar con OPENAPI_ENABLED=false: entonces responde 404).
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                // Gestión de cuentas, permisos y auditoría: solo ADMIN (CA08.4). El ADMIN no ve imágenes.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Renovar, cerrar sesión y consultar el perfil: cualquier rol con sesión vigente.
                .requestMatchers("/api/auth/**").authenticated()
                // Imágenes: solo MEDICO (con permiso ACCESO_ESTUDIOS, que valida el proxy) y PACIENTE (sus estudios).
                .requestMatchers("/api/dicomweb/**").hasAnyRole("MEDICO", "PACIENTE")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(errores).accessDeniedHandler(errores))
            // Clave temporal: solo se puede cambiar la clave, ver el perfil o cerrar sesión.
            .addFilterAfter(new ClaveTemporalFilter(errores), BearerTokenAuthenticationFilter.class)
            .oauth2ResourceServer(oauth -> oauth
                .authenticationEntryPoint(errores)
                .accessDeniedHandler(errores)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter())));
        return http.build();
    }

    private JwtAuthenticationConverter converter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
