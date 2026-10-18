package pe.upn.visorpacs.auditoria;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserta el evento en la transacción en curso, si la hay (una sola conexión por solicitud), o abre una si no la hay.
 * Antes usaba una transacción independiente (REQUIRES_NEW), que obligaba a tener dos conexiones a la vez: con
 * tantos inicios de sesión simultáneos como conexiones tiene el pool, todos retenían una y esperaban la segunda
 * hasta agotar el tiempo de espera de 30 s (defecto D-19).
 * Los eventos de fallo (clave incorrecta, enlace inválido) se conservan porque los métodos que los registran
 * declaran noRollbackFor para esas excepciones; EventoAuditoria recorta los textos al largo de cada columna,
 * de modo que un valor largo no hace fallar el INSERT.
 */
@Component
public class AuditoriaWriter {
    private final AuditoriaRepository repositorio;

    public AuditoriaWriter(AuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public void guardar(EventoAuditoria evento) {
        repositorio.save(evento);
    }
}
