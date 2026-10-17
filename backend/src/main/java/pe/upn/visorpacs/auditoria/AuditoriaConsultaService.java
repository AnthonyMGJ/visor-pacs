package pe.upn.visorpacs.auditoria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upn.visorpacs.web.PaginaDto;

/** Consulta de auditoría para el administrador (CA10.3): filtra por usuario, tipo, resultado, estudio y fechas. Solo lectura. */
@Service
public class AuditoriaConsultaService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public PaginaDto<EventoDto> buscar(String username, TipoEvento tipo, ResultadoEvento resultado, String estudio,
                                       Instant desde, Instant hasta, int pagina, int tamano) {
        Pageable pedido = PaginaDto.pedido(pagina, tamano, Sort.unsorted());
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<EventoAuditoria> consulta = cb.createQuery(EventoAuditoria.class);
        Root<EventoAuditoria> raiz = consulta.from(EventoAuditoria.class);
        consulta.select(raiz)
                .where(filtros(cb, raiz, username, tipo, resultado, estudio, desde, hasta))
                .orderBy(cb.desc(raiz.get("fecha")), cb.desc(raiz.get("id"))); // lo más reciente primero
        List<EventoAuditoria> filas = em.createQuery(consulta)
                .setFirstResult((int) pedido.getOffset())
                .setMaxResults(pedido.getPageSize())
                .getResultList();

        CriteriaQuery<Long> conteo = cb.createQuery(Long.class);
        Root<EventoAuditoria> raizConteo = conteo.from(EventoAuditoria.class);
        conteo.select(cb.count(raizConteo))
                .where(filtros(cb, raizConteo, username, tipo, resultado, estudio, desde, hasta));
        long total = em.createQuery(conteo).getSingleResult();

        return PaginaDto.de(filas.stream().map(EventoDto::de).toList(), pedido, total);
    }

    private static Predicate[] filtros(CriteriaBuilder cb, Root<EventoAuditoria> raiz, String username,
                                       TipoEvento tipo, ResultadoEvento resultado, String estudio,
                                       Instant desde, Instant hasta) {
        List<Predicate> p = new ArrayList<>();
        if (username != null && !username.isBlank()) {
            p.add(cb.equal(raiz.get("username"), username.trim()));
        }
        if (tipo != null) {
            p.add(cb.equal(raiz.get("tipo"), tipo.name()));
        }
        if (resultado != null) {
            p.add(cb.equal(raiz.get("resultado"), resultado.name()));
        }
        if (estudio != null && !estudio.isBlank()) {
            p.add(cb.equal(raiz.get("estudio"), estudio.trim()));
        }
        if (desde != null) {
            p.add(cb.greaterThanOrEqualTo(raiz.<LocalDateTime>get("fecha"), LocalDateTime.ofInstant(desde, ZoneOffset.UTC)));
        }
        if (hasta != null) {
            p.add(cb.lessThanOrEqualTo(raiz.<LocalDateTime>get("fecha"), LocalDateTime.ofInstant(hasta, ZoneOffset.UTC)));
        }
        return p.toArray(new Predicate[0]);
    }
}
