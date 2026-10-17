package pe.upn.visorpacs.sesion;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.upn.visorpacs.repository.UsuarioRepository;

/**
 * Memoria breve del estado de las cuentas, para no consultar la base en cada una de las cientos de
 * peticiones que genera abrir una serie. Se invalida de inmediato cuando esta aplicación cambia la cuenta
 * (desactivar, permisos, cerrar sesión); el TTL solo cubre cambios hechos por otra vía.
 */
@Component
public class EstadoCuentaCache {

    private static final int MAX_ENTRADAS = 10_000;

    private record Entrada(EstadoCuenta estado, long venceMs) {}

    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final long ttlMs;
    private final ConcurrentHashMap<Integer, Entrada> entradas = new ConcurrentHashMap<>();

    public EstadoCuentaCache(UsuarioRepository usuarios, Clock clock, SesionProperties props) {
        this.usuarios = usuarios;
        this.clock = clock;
        this.ttlMs = Math.max(0, props.cacheCuentaSegundos()) * 1000L;
    }

    /** Estado vigente de la cuenta, o vacío si el usuario ya no existe. */
    public Optional<EstadoCuenta> obtener(int idUsuario) {
        long ahora = clock.millis();
        Entrada e = entradas.get(idUsuario);
        if (e != null && e.venceMs() > ahora) {
            return Optional.of(e.estado());
        }
        Optional<EstadoCuenta> cargado = usuarios.findById(idUsuario).map(EstadoCuenta::de);
        if (cargado.isEmpty()) {
            entradas.remove(idUsuario);
            return cargado;
        }
        if (ttlMs > 0) {
            if (entradas.size() >= MAX_ENTRADAS) {
                entradas.clear();
            }
            entradas.put(idUsuario, new Entrada(cargado.get(), ahora + ttlMs));
        }
        return cargado;
    }

    public void invalidar(int idUsuario) {
        entradas.remove(idUsuario);
    }

    /**
     * Invalida cuando la transacción en curso confirma (si no la confirma, el estado real no cambió).
     * Sin transacción activa invalida de inmediato.
     */
    public void invalidarTrasCommit(int idUsuario) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidar(idUsuario);
                }
            });
        } else {
            invalidar(idUsuario);
        }
    }
}
