package pe.upn.visorpacs.auditoria;

import org.springframework.data.repository.Repository;

/**
 * Solo permite insertar. A propósito NO extiende JpaRepository ni JpaSpecificationExecutor (que traen
 * delete): no hay forma de borrar ni de actualizar desde el código (CA10.4). La consulta del administrador
 * se hace con Criteria de solo lectura en AuditoriaConsultaService; la base de datos lo refuerza con triggers.
 */
public interface AuditoriaRepository extends Repository<EventoAuditoria, Long> {
    EventoAuditoria save(EventoAuditoria evento);
}
