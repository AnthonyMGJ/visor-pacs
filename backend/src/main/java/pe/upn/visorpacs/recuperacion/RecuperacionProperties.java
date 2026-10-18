package pe.upn.visorpacs.recuperacion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Recuperación de clave por correo (RF-09, prefijo app.recuperacion).
 * habilitada: interruptor general. Apagado, los dos endpoints responden 404 y el login no muestra el enlace.
 * vigenciaMinutos: validez del enlace. maxPorCuentaHora: solicitudes que envían correo por cuenta y por hora.
 * maxPorIp / ventanaIpSegundos: límite de solicitudes por IP a cada endpoint (0 = sin límite; responde 429).
 * frontendUrl: base del enlace del correo. Siempre sale de la configuración, nunca de la cabecera Host.
 */
@ConfigurationProperties(prefix = "app.recuperacion")
public record RecuperacionProperties(@DefaultValue("false") boolean habilitada,
                                     @DefaultValue("15") long vigenciaMinutos,
                                     @DefaultValue("3") int maxPorCuentaHora,
                                     @DefaultValue("10") int maxPorIp,
                                     @DefaultValue("3600") long ventanaIpSegundos,
                                     @DefaultValue("http://localhost:5173") String frontendUrl,
                                     @DefaultValue("no-reply@localhost") String remitente) {}
