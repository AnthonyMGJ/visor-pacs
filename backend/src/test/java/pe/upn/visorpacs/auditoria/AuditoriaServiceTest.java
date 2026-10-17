package pe.upn.visorpacs.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditoriaServiceTest {

    static class RelojManual extends Clock {
        private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-01T10:00:00Z"));
        void avanzarMinutos(long m) { ahora.updateAndGet(i -> i.plusSeconds(m * 60)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    }

    private final AuditoriaWriter writer = mock(AuditoriaWriter.class);
    private final RelojManual reloj = new RelojManual();
    private AuditoriaService servicio;

    @BeforeEach
    void preparar() {
        RequestContextHolder.resetRequestAttributes();
        servicio = new AuditoriaService(writer, reloj, new AuditoriaProperties(15));
    }

    private EventoAuditoria capturar(int veces) {
        ArgumentCaptor<EventoAuditoria> c = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(writer, times(veces)).guardar(c.capture());
        return c.getValue();
    }

    @Test
    void guardaUsuarioTipoFechaEIpDeLaPeticion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        servicio.registrar(TipoEvento.INICIO_SESION, ResultadoEvento.EXITO, 7, "medico.demo", "MEDICO", null, null);

        EventoAuditoria e = capturar(1);
        assertEquals("INICIO_SESION", e.getTipo());
        assertEquals("EXITO", e.getResultado());
        assertEquals(7, e.getIdUsuario());
        assertEquals("medico.demo", e.getUsername());
        assertEquals("203.0.113.9", e.getIp());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 1, 10, 0), e.getFecha());
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void fueraDeUnaPeticionHttpLaIpQuedaVacia() {
        servicio.registrar(TipoEvento.INICIO_SESION, ResultadoEvento.FALLO, null, "nadie", null, null, "x");
        assertNull(capturar(1).getIp());
    }

    @Test
    void siLaBaseFallaLaOperacionDelUsuarioContinua() {
        doThrow(new IllegalStateException("base caída")).when(writer).guardar(any());
        assertDoesNotThrow(() ->
                servicio.registrar(TipoEvento.INICIO_SESION, ResultadoEvento.EXITO, 7, "medico.demo", "MEDICO", null, null));
    }

    @Test
    void laAperturaDeUnMismoEstudioSeRegistraUnaVezPorVentana() {
        for (int i = 0; i < 50; i++) { // abrir una serie genera muchas peticiones
            servicio.registrarAccesoEstudio(TipoEvento.APERTURA_ESTUDIO, ResultadoEvento.EXITO, 7, "medico.demo", "MEDICO", "1.2.3", null);
        }
        verify(writer, times(1)).guardar(any());

        reloj.avanzarMinutos(16);
        servicio.registrarAccesoEstudio(TipoEvento.APERTURA_ESTUDIO, ResultadoEvento.EXITO, 7, "medico.demo", "MEDICO", "1.2.3", null);
        verify(writer, times(2)).guardar(any());
    }

    @Test
    void estudiosOUsuariosDistintosSiSeRegistranPorSeparado() {
        servicio.registrarAccesoEstudio(TipoEvento.APERTURA_ESTUDIO, ResultadoEvento.EXITO, 7, "a", "MEDICO", "1.2.3", null);
        servicio.registrarAccesoEstudio(TipoEvento.APERTURA_ESTUDIO, ResultadoEvento.EXITO, 7, "a", "MEDICO", "4.5.6", null);
        servicio.registrarAccesoEstudio(TipoEvento.APERTURA_ESTUDIO, ResultadoEvento.EXITO, 8, "b", "MEDICO", "1.2.3", null);
        servicio.registrarAccesoEstudio(TipoEvento.ACCESO_ESTUDIO_DENEGADO, ResultadoEvento.FALLO, 7, "a", "MEDICO", "1.2.3", "x");
        verify(writer, times(4)).guardar(any());
    }

    @Test
    void losCamposLargosSeRecortanAlTamanoDeLaColumna() {
        servicio.registrar(TipoEvento.INICIO_SESION, ResultadoEvento.FALLO, null, "x".repeat(80), null, null, "d".repeat(400));
        EventoAuditoria e = capturar(1);
        assertEquals(50, e.getUsername().length());
        assertEquals(255, e.getDetalle().length());
    }
}
