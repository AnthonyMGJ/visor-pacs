package pe.upn.visorpacs.config;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contador de ventana fija por IP, en memoria. Devuelve 0 si la solicitud puede pasar, o los segundos que
 * faltan para que se libere el cupo si ya se superó el máximo de la ventana.
 * La memoria está acotada: si se llena, se descartan primero las ventanas vencidas y, de seguir llena, todas.
 */
public class LimiteIntentosPorIp {

    private static final int MAX_IPS = 50_000;

    private record Ventana(long inicioMs, int cuenta) {}

    private final Clock clock;
    private final int maximo;
    private final long ventanaMs;
    private final ConcurrentHashMap<String, Ventana> porIp = new ConcurrentHashMap<>();
    private final AtomicLong bloqueadas = new AtomicLong();

    public LimiteIntentosPorIp(Clock clock, int maximo, long ventanaSegundos) {
        this.clock = clock;
        this.maximo = maximo;
        this.ventanaMs = Math.max(1, ventanaSegundos) * 1000L;
    }

    public boolean activo() {
        return maximo > 0;
    }

    /** @return 0 si pasa; si no, segundos (al menos 1) hasta que se libere el cupo. */
    public long registrar(String ip) {
        if (!activo()) {
            return 0;
        }
        long ahora = clock.millis();
        if (porIp.size() >= MAX_IPS) {
            porIp.values().removeIf(v -> ahora - v.inicioMs() >= ventanaMs);
            if (porIp.size() >= MAX_IPS) {
                porIp.clear();
            }
        }
        long[] espera = {0};
        porIp.compute(ip == null ? "?" : ip, (k, v) -> {
            if (v == null || ahora - v.inicioMs() >= ventanaMs) {
                return new Ventana(ahora, 1);
            }
            if (v.cuenta() >= maximo) {
                espera[0] = Math.max(1, (v.inicioMs() + ventanaMs - ahora + 999) / 1000);
                return v; // no se sigue contando: el cupo se libera al terminar la ventana
            }
            return new Ventana(v.inicioMs(), v.cuenta() + 1);
        });
        if (espera[0] > 0) {
            bloqueadas.incrementAndGet();
        }
        return espera[0];
    }

    public long totalBloqueadas() {
        return bloqueadas.get();
    }
}
