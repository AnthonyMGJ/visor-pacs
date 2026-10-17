package pe.upn.visorpacs.auditoria;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ajustes de auditoría (prefijo app.auditoria).
 * aperturaVentanaMinutos: abrir un estudio genera cientos de peticiones; la apertura se registra una vez
 * por usuario y estudio dentro de esta ventana.
 */
@ConfigurationProperties(prefix = "app.auditoria")
public record AuditoriaProperties(@DefaultValue("15") long aperturaVentanaMinutos) {}
