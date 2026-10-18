package pe.upn.visorpacs.recuperacion;

import java.time.Instant;
import java.util.Optional;

/** Almacén de los enlaces de recuperación. Solo guarda el hash del token. */
public interface AlmacenRecuperacion {

    /** Un registro pendiente o ya resuelto, tal como está en la base. */
    record Registro(int idUsuario, Instant venceEn, Instant usadoEn) {}

    void guardar(int idUsuario, String tokenHash, Instant creadoEn, Instant venceEn, String ip);

    /** Solicitudes de la cuenta creadas desde el instante dado (cuenta también las ya usadas o anuladas). */
    int contarDesde(int idUsuario, Instant desde);

    Optional<Registro> buscar(String tokenHash);

    /** Marca como usado el token solo si sigue pendiente y vigente. true = este llamado lo consumió. */
    boolean consumir(String tokenHash, Instant ahora);

    /** Anula los enlaces pendientes de la cuenta; devuelve cuántos anuló. */
    int anularPendientes(int idUsuario, Instant ahora);

    /** Borra registros vencidos antes del instante dado. */
    void purgarAnteriores(Instant limite);
}
