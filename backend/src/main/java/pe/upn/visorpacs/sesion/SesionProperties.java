package pe.upn.visorpacs.sesion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ajustes de la validación de sesión en cada petición (prefijo app.sesion).
 * cacheCuentaSegundos: cuánto tiempo se recuerda el estado de una cuenta antes de volver a leerlo de la base.
 * Los cambios hechos desde esta misma aplicación invalidan la entrada al instante; el tiempo solo cubre
 * cambios hechos por otra vía (otra instancia, SQL directo).
 */
@ConfigurationProperties(prefix = "app.sesion")
public record SesionProperties(@DefaultValue("30") long cacheCuentaSegundos) {}
