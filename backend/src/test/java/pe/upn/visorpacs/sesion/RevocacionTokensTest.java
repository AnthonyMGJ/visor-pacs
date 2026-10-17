package pe.upn.visorpacs.sesion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RevocacionTokensTest {

    /** Almacén en memoria que cuenta cuántas veces se leyó y puede fallar al guardar. */
    static class AlmacenFalso implements AlmacenRevocaciones {
        final Map<String, Instant> filas = new HashMap<>();
        final AtomicInteger lecturas = new AtomicInteger();
        boolean fallarAlGuardar;

        @Override public void guardar(String jti, int idUsuario, Instant expiraEn, Instant revocadoEn) {
            if (fallarAlGuardar) throw new IllegalStateException("base caída");
            filas.put(jti, expiraEn);
        }
        @Override public Map<String, Instant> cargarVigentes(Instant ahora) {
            lecturas.incrementAndGet();
            Map<String, Instant> r = new HashMap<>();
            filas.forEach((k, v) -> { if (v.isAfter(ahora)) r.put(k, v); });
            return r;
        }
        @Override public void purgarVencidos(Instant ahora) { filas.values().removeIf(v -> !v.isAfter(ahora)); }
    }

    private final Instant t0 = Instant.parse("2026-10-17T12:00:00Z");
    private final AlmacenFalso almacen = new AlmacenFalso();

    private RevocacionTokens con(Clock reloj) {
        return new RevocacionTokens(almacen, reloj);
    }

    @Test
    void unTokenRevocadoSeRechazaYOtroNo() {
        RevocacionTokens r = con(Clock.fixed(t0, ZoneOffset.UTC));
        r.revocar("a", 7, t0.plusSeconds(900));

        assertTrue(r.estaRevocado("a"));
        assertFalse(r.estaRevocado("b"));
        assertFalse(r.estaRevocado(null));
        assertTrue(almacen.filas.containsKey("a"));
    }

    @Test
    void laRevocacionDejaDeImportarCuandoElTokenVencePorSiSolo() {
        RevocacionTokens antes = con(Clock.fixed(t0, ZoneOffset.UTC));
        antes.revocar("a", 7, t0.plusSeconds(900));

        RevocacionTokens despues = con(Clock.fixed(t0.plusSeconds(901), ZoneOffset.UTC));
        despues.revocar("otro", 7, t0.plusSeconds(2000)); // revocar purga lo vencido
        assertFalse(despues.estaRevocado("a"));
        assertFalse(almacen.filas.containsKey("a"));
    }

    @Test
    void trasUnReinicioSeRecuperanLasRevocacionesVigentesDeLaBase() {
        almacen.filas.put("persistido", t0.plusSeconds(600));
        almacen.filas.put("viejo", t0.minusSeconds(10));

        RevocacionTokens r = con(Clock.fixed(t0, ZoneOffset.UTC)); // instancia nueva = reinicio

        assertTrue(r.estaRevocado("persistido"));
        assertFalse(r.estaRevocado("viejo"));
    }

    @Test
    void laBaseSeLeeUnaSolaVez() {
        RevocacionTokens r = con(Clock.fixed(t0, ZoneOffset.UTC));
        r.estaRevocado("x");
        r.estaRevocado("y");
        r.revocar("z", 1, t0.plusSeconds(60));
        assertEquals(1, almacen.lecturas.get());
    }

    @Test
    void siNoSePuedeGuardarElCierreFallaEnVozAltaYNoQuedaRevocadoEnMemoria() {
        RevocacionTokens r = con(Clock.fixed(t0, ZoneOffset.UTC));
        almacen.fallarAlGuardar = true;

        assertThrows(IllegalStateException.class, () -> r.revocar("a", 7, t0.plusSeconds(900)));
        assertFalse(r.estaRevocado("a"));
    }

    @Test
    void ignoraDatosIncompletos() {
        RevocacionTokens r = con(Clock.fixed(t0, ZoneOffset.UTC));
        r.revocar(null, 7, t0.plusSeconds(10));
        r.revocar(" ", 7, t0.plusSeconds(10));
        r.revocar("a", 7, null);
        assertTrue(almacen.filas.isEmpty());
    }
}
