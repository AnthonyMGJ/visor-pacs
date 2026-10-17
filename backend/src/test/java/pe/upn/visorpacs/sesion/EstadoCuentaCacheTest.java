package pe.upn.visorpacs.sesion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Permisos;
import pe.upn.visorpacs.domain.Rol;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.UsuarioRepository;

class EstadoCuentaCacheTest {

    /** Reloj que se puede adelantar a mano. */
    static class RelojManual extends Clock {
        private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-01T10:00:00Z"));
        void avanzarSegundos(long s) { ahora.updateAndGet(i -> i.plusSeconds(s)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    }

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RelojManual reloj = new RelojManual();
    private EstadoCuentaCache cache;
    private Usuario medico;

    @BeforeEach
    void preparar() {
        cache = new EstadoCuentaCache(usuarios, reloj, new SesionProperties(30));
        medico = Usuario.nuevo("medico.demo", "hash", "Ana", "Pérez", "ana@example.com", new Rol("MEDICO"));
        ReflectionTestUtils.setField(medico, "id", 7); // la fila real ya tiene id; el registro EstadoCuenta lo exige
        medico.otorgar(new Permiso(Permisos.ACCESO_ESTUDIOS, "x"));
        when(usuarios.findById(7)).thenReturn(Optional.of(medico));
    }

    @Test
    void laSegundaConsultaDentroDelTtlNoVaALaBase() {
        cache.obtener(7);
        cache.obtener(7);
        verify(usuarios, times(1)).findById(7);
    }

    @Test
    void pasadoElTtlVuelveALeerLaBase() {
        cache.obtener(7);
        reloj.avanzarSegundos(31);
        cache.obtener(7);
        verify(usuarios, times(2)).findById(7);
    }

    @Test
    void invalidarFuerzaUnaLecturaNueva() {
        cache.obtener(7);
        cache.invalidar(7);
        cache.obtener(7);
        verify(usuarios, times(2)).findById(7);
    }

    @Test
    void reflejaElEstadoNuevoTrasInvalidar() {
        assertTrue(cache.obtener(7).orElseThrow().activo());
        medico.setActivo(false);
        medico.invalidarSesiones();
        assertTrue(cache.obtener(7).orElseThrow().activo(), "mientras no se invalide, se sirve la copia");
        cache.invalidar(7);
        EstadoCuenta nuevo = cache.obtener(7).orElseThrow();
        assertFalse(nuevo.activo());
        assertEquals(1, nuevo.sesionVersion());
    }

    @Test
    void usuarioInexistenteDaVacioYNoSeRecuerda() {
        when(usuarios.findById(99)).thenReturn(Optional.empty());
        assertTrue(cache.obtener(99).isEmpty());
        assertTrue(cache.obtener(99).isEmpty());
        verify(usuarios, times(2)).findById(99);
    }

    @Test
    void conTtlCeroNuncaRecuerda() {
        EstadoCuentaCache sinMemoria = new EstadoCuentaCache(usuarios, reloj, new SesionProperties(0));
        sinMemoria.obtener(7);
        sinMemoria.obtener(7);
        verify(usuarios, times(2)).findById(7);
    }

    @Test
    void elEstadoIncluyePermisosYHabilitacion() {
        EstadoCuenta e = cache.obtener(7).orElseThrow();
        assertTrue(e.tienePermiso(Permisos.ACCESO_ESTUDIOS));
        assertTrue(e.habilitada());
        assertEquals("MEDICO", e.rol());
    }
}
