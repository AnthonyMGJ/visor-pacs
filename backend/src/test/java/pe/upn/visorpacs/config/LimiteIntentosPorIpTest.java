package pe.upn.visorpacs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LimiteIntentosPorIpTest {

    /** Reloj que se mueve a mano. */
    static class RelojManual extends Clock {
        Instant ahora = Instant.parse("2026-10-17T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
    }

    private final RelojManual reloj = new RelojManual();

    @Test
    void dejaPasarHastaElMaximoYLuegoIndicaCuantoEsperar() {
        LimiteIntentosPorIp limite = new LimiteIntentosPorIp(reloj, 3, 60);
        assertEquals(0, limite.registrar("1.1.1.1"));
        assertEquals(0, limite.registrar("1.1.1.1"));
        assertEquals(0, limite.registrar("1.1.1.1"));

        long espera = limite.registrar("1.1.1.1");

        assertEquals(60, espera);
        assertEquals(1, limite.totalBloqueadas());
        reloj.avanzar(Duration.ofSeconds(20));
        assertEquals(40, limite.registrar("1.1.1.1"));
    }

    @Test
    void cadaIpTieneSuPropioCupo() {
        LimiteIntentosPorIp limite = new LimiteIntentosPorIp(reloj, 1, 60);
        assertEquals(0, limite.registrar("1.1.1.1"));
        assertTrue(limite.registrar("1.1.1.1") > 0);
        assertEquals(0, limite.registrar("2.2.2.2"));
    }

    @Test
    void elCupoSeLiberaAlTerminarLaVentana() {
        LimiteIntentosPorIp limite = new LimiteIntentosPorIp(reloj, 2, 60);
        limite.registrar("1.1.1.1");
        limite.registrar("1.1.1.1");
        assertTrue(limite.registrar("1.1.1.1") > 0);

        reloj.avanzar(Duration.ofSeconds(60));

        assertEquals(0, limite.registrar("1.1.1.1"));
        assertEquals(0, limite.registrar("1.1.1.1"));
        assertTrue(limite.registrar("1.1.1.1") > 0);
    }

    @Test
    void lasSolicitudesRechazadasNoAlargaLaVentana() {
        LimiteIntentosPorIp limite = new LimiteIntentosPorIp(reloj, 1, 60);
        limite.registrar("1.1.1.1");
        for (int i = 0; i < 20; i++) {
            reloj.avanzar(Duration.ofSeconds(2));
            limite.registrar("1.1.1.1"); // insistir no empuja la liberación más allá de los 60 s originales
        }
        reloj.avanzar(Duration.ofSeconds(20));
        assertEquals(0, limite.registrar("1.1.1.1"));
    }

    @Test
    void conMaximoCeroEstaDesactivado() {
        LimiteIntentosPorIp limite = new LimiteIntentosPorIp(reloj, 0, 60);
        assertFalse(limite.activo());
        for (int i = 0; i < 1000; i++) {
            assertEquals(0, limite.registrar("1.1.1.1"));
        }
    }

    @Test
    void unaIpDesconocidaTambienSeLimita() {
        LimiteIntentosPorIp limite = new LimiteIntentosPorIp(reloj, 1, 60);
        assertEquals(0, limite.registrar(null));
        assertTrue(limite.registrar(null) > 0);
    }
}
