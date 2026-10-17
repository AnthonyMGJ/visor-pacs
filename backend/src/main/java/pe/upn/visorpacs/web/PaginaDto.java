package pe.upn.visorpacs.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Respuesta paginada estable (no se expone PageImpl de Spring Data, cuyo JSON no es un contrato). */
public record PaginaDto<T>(List<T> contenido, int pagina, int tamano, long total, int totalPaginas) {

    public static final int TAMANO_MAXIMO = 100;

    public static <E, T> PaginaDto<T> de(Page<E> page, Function<E, T> mapeo) {
        return new PaginaDto<>(page.getContent().stream().map(mapeo).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Para consultas armadas a mano: el contenido ya está recortado a la página pedida. */
    public static <T> PaginaDto<T> de(List<T> contenido, Pageable pedido, long total) {
        int tamano = pedido.getPageSize();
        return new PaginaDto<>(contenido, pedido.getPageNumber(), tamano, total, (int) ((total + tamano - 1) / tamano));
    }

    /** Página base 0; el tamaño se acota a 1..100 para que nadie pida la tabla completa de una vez. */
    public static Pageable pedido(int pagina, int tamano, Sort orden) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO), orden);
    }
}
