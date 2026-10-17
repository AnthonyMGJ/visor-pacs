package pe.upn.visorpacs.auditoria;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserta en una transacción propia: el evento se conserva aunque la operación que lo originó se revierta,
 * y un fallo de auditoría no marca como "solo reversión" la transacción de quien lo llama.
 */
@Component
public class AuditoriaWriter {
    private final AuditoriaRepository repositorio;

    public AuditoriaWriter(AuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardar(EventoAuditoria evento) {
        repositorio.save(evento);
    }
}
