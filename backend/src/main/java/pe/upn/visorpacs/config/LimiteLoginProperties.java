package pe.upn.visorpacs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Límite de solicitudes de inicio de sesión por dirección IP (prefijo app.limite-login).
 * Complementa el bloqueo por cuenta (varias cuentas atacadas desde una misma IP).
 * maxSolicitudes = 0 desactiva el límite.
 */
@ConfigurationProperties(prefix = "app.limite-login")
public record LimiteLoginProperties(@DefaultValue("30") int maxSolicitudes,
                                    @DefaultValue("60") long ventanaSegundos) {}
