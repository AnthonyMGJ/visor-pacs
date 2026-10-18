package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * D-19: inicios de sesión simultáneos con un pool de conexiones pequeño. Cuando la auditoría usaba una transacción
 * independiente (segunda conexión), 16 inicios simultáneos con un pool de 4 agotaban el pool y los eventos se perdían
 * tras el tiempo de espera. Ahora cada solicitud usa una sola conexión: todos responden 200 y todos quedan auditados.
 * Usa su propio contexto con un pool de 4 conexiones y 5 s de espera máxima por conexión.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=5000"})
class ConcurrenciaLoginIT extends IntegracionBase {

    @Test
    void variosInicioDeSesionSimultaneosNoAgotanElPoolYTodosQuedanAuditados() throws Exception {
        final int simultaneos = 16;
        Cuenta m = medico();
        ExecutorService hilos = Executors.newFixedThreadPool(simultaneos);
        CountDownLatch listos = new CountDownLatch(simultaneos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> respuestas = new ArrayList<>();
        try {
            for (int i = 0; i < simultaneos; i++) {
                respuestas.add(hilos.submit(() -> {
                    listos.countDown();
                    salida.await();
                    return mvc.perform(post("/api/auth/login").contentType(JSON)
                                    .content(cuerpo(Map.of("username", m.username(), "password", CLAVE))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(listos.await(10, TimeUnit.SECONDS));
            long inicio = System.nanoTime();
            salida.countDown();
            for (Future<Integer> r : respuestas) {
                assertEquals(200, r.get(30, TimeUnit.SECONDS));
            }
            long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
            // Con el defecto, cada inicio tardaba cerca del tiempo de espera (5 s en esta prueba).
            assertTrue(milisegundos < 4500, "Los inicios de sesión simultáneos tardaron " + milisegundos + " ms");
        } finally {
            hilos.shutdownNow();
        }
        Integer auditados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evento_auditoria WHERE username = ? AND tipo = 'INICIO_SESION' AND resultado = 'EXITO'",
                Integer.class, m.username());
        assertEquals(simultaneos, auditados);
    }
}
