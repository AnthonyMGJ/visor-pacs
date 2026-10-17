package pe.upn.visorpacs.sesion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tope absoluto de una sesión (prefijo app.sesion). Aunque el usuario siga activo y renueve el token,
 * pasadas estas horas desde el inicio de sesión con clave debe volver a escribirla.
 */
@ConfigurationProperties(prefix = "app.sesion")
public record SesionDuracionProperties(@DefaultValue("8") long duracionMaximaHoras) {}
