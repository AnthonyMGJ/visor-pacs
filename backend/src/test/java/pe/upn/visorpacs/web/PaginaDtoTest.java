package pe.upn.visorpacs.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginaDtoTest {

    @Test
    void elTamanoSeAcotaEntre1Y100() {
        assertEquals(100, PaginaDto.pedido(0, 100000, Sort.unsorted()).getPageSize());
        assertEquals(1, PaginaDto.pedido(0, 0, Sort.unsorted()).getPageSize());
        assertEquals(1, PaginaDto.pedido(0, -5, Sort.unsorted()).getPageSize());
        assertEquals(20, PaginaDto.pedido(0, 20, Sort.unsorted()).getPageSize());
    }

    @Test
    void laPaginaNegativaSeTrataComoCero() {
        assertEquals(0, PaginaDto.pedido(-3, 10, Sort.unsorted()).getPageNumber());
    }

    @Test
    void conviertePageEnUnContratoEstable() {
        Pageable p = PageRequest.of(1, 2);
        PaginaDto<String> dto = PaginaDto.de(new PageImpl<>(List.of(3, 4), p, 7), n -> "n" + n);
        assertEquals(List.of("n3", "n4"), dto.contenido());
        assertEquals(1, dto.pagina());
        assertEquals(2, dto.tamano());
        assertEquals(7, dto.total());
        assertEquals(4, dto.totalPaginas());
    }

    @Test
    void armaLaPaginaDesdeUnaListaYUnTotal() {
        PaginaDto<String> dto = PaginaDto.de(List.of("a", "b"), PageRequest.of(2, 2), 5);
        assertEquals(2, dto.pagina());
        assertEquals(3, dto.totalPaginas());
        assertEquals(5, dto.total());
        assertEquals(0, PaginaDto.de(List.<String>of(), PageRequest.of(0, 10), 0).totalPaginas());
    }
}
