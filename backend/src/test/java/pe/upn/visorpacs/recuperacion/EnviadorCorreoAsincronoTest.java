package pe.upn.visorpacs.recuperacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** CA09.12: un servidor de correo lento o caído no retrasa ni rompe la solicitud del usuario. */
class EnviadorCorreoAsincronoTest {

    private final ExecutorService ejecutor = Executors.newFixedThreadPool(2);

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    @Test
    void conUnSmtpLentoLaLlamadaVuelveEnMenosDeMedioSegundo() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        CountDownLatch terminado = new CountDownLatch(1);
        EnviadorCorreo lento = (para, asunto, texto) -> {
            try {
                liberar.await(5, TimeUnit.SECONDS); // simula un SMTP que tarda segundos
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            terminado.countDown();
        };
        EnviadorCorreoAsincrono asincrono = new EnviadorCorreoAsincrono(lento, ejecutor);

        long inicio = System.nanoTime();
        asincrono.enviar("a@b.pe", "asunto", "texto");
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertTrue(ms < 500, "enviar() debe volver de inmediato, tardó " + ms + " ms");
        liberar.countDown();
        assertTrue(terminado.await(2, TimeUnit.SECONDS), "el envío igual se completa en segundo plano");
    }

    @Test
    void siElSmtpFallaElErrorNoLlegaAlQueLlama() throws Exception {
        CountDownLatch intento = new CountDownLatch(1);
        EnviadorCorreo caido = (para, asunto, texto) -> {
            intento.countDown();
            throw new IllegalStateException("Connection refused");
        };
        new EnviadorCorreoAsincrono(caido, ejecutor).enviar("a@b.pe", "asunto", "texto"); // no lanza
        assertTrue(intento.await(2, TimeUnit.SECONDS));
    }

    @Test
    void siLaColaEstaLlenaSeDescartaSinLanzarError() {
        AtomicInteger llamadas = new AtomicInteger();
        Executor lleno = r -> { throw new RejectedExecutionException("cola llena"); };
        EnviadorCorreo delegado = (para, asunto, texto) -> llamadas.incrementAndGet();

        new EnviadorCorreoAsincrono(delegado, lleno).enviar("a@b.pe", "asunto", "texto"); // no lanza

        assertEquals(0, llamadas.get());
    }

    @Test
    void entregaAlDelegadoLosMismosDatos() throws Exception {
        String[] visto = new String[3];
        CountDownLatch listo = new CountDownLatch(1);
        EnviadorCorreo delegado = (para, asunto, texto) -> {
            visto[0] = para; visto[1] = asunto; visto[2] = texto;
            listo.countDown();
        };
        new EnviadorCorreoAsincrono(delegado, ejecutor).enviar("x@y.pe", "Hola", "Cuerpo");
        assertTrue(listo.await(2, TimeUnit.SECONDS));
        assertEquals("x@y.pe", visto[0]);
        assertEquals("Hola", visto[1]);
        assertEquals("Cuerpo", visto[2]);
    }
}
