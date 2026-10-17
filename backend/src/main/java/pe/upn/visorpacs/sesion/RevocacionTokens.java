package pe.upn.visorpacs.sesion;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Revocación de un token concreto (HU12). Se consulta en cada petición, así que la lista vive en memoria
 * y se respalda en la tabla token_revocado para sobrevivir a un reinicio del backend.
 * Una entrada solo se necesita hasta que el token vence por sí mismo.
 *
 * Límite conocido: la lista en memoria es de ESTA instancia. Con varias instancias del backend habría que
 * consultar la tabla (o un Redis); el proyecto corre una sola instancia.
 */
@Component
public class RevocacionTokens {

    private static final Logger log = LoggerFactory.getLogger(RevocacionTokens.class);

    private final AlmacenRevocaciones almacen;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> revocados = new ConcurrentHashMap<>();
    private volatile boolean cargado;

    public RevocacionTokens(AlmacenRevocaciones almacen, Clock clock) {
        this.almacen = almacen;
        this.clock = clock;
    }

    public void revocar(String jti, int idUsuario, Instant expiraEn) {
        if (jti == null || jti.isBlank() || expiraEn == null) {
            return;
        }
        cargarSiHaceFalta();
        Instant ahora = clock.instant();
        almacen.guardar(jti, idUsuario, expiraEn, ahora); // si falla, lanza: el cierre no debe aparentar éxito
        revocados.put(jti, expiraEn);
        purgar(ahora);
    }

    public boolean estaRevocado(String jti) {
        if (jti == null) {
            return false;
        }
        cargarSiHaceFalta();
        Instant vence = revocados.get(jti);
        return vence != null && vence.isAfter(clock.instant());
    }

    private void cargarSiHaceFalta() {
        if (cargado) {
            return;
        }
        synchronized (this) {
            if (!cargado) {
                revocados.putAll(almacen.cargarVigentes(clock.instant()));
                cargado = true;
                log.info("Revocaciones individuales cargadas: {}", revocados.size());
            }
        }
    }

    private void purgar(Instant ahora) {
        revocados.values().removeIf(vence -> !vence.isAfter(ahora));
        try {
            almacen.purgarVencidos(ahora);
        } catch (RuntimeException e) {
            log.warn("No se pudieron purgar los tokens revocados vencidos: {}", e.toString());
        }
    }
}
