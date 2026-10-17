package pe.upn.visorpacs.auditoria;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.upn.visorpacs.web.PaginaDto;

/** Solo ADMIN (ver SecurityConfig): consulta del registro de accesos. No existe ninguna operación de escritura. */
@RestController
@RequestMapping("/api/admin/auditoria")
@Tag(name = "Auditoría", description = "Registro de accesos (solo lectura, rol ADMIN)")
public class AuditoriaController {
    private final AuditoriaConsultaService consulta;

    public AuditoriaController(AuditoriaConsultaService consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    @Operation(summary = "Lista eventos de auditoría, del más reciente al más antiguo",
            description = "Filtros opcionales. Las fechas son instantes ISO-8601 en UTC, p. ej. 2026-10-16T05:00:00Z.")
    public PaginaDto<EventoDto> listar(@RequestParam(required = false) String usuario,
                                       @RequestParam(required = false) TipoEvento tipo,
                                       @RequestParam(required = false) ResultadoEvento resultado,
                                       @RequestParam(required = false) String estudio,
                                       @RequestParam(required = false) Instant desde,
                                       @RequestParam(required = false) Instant hasta,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "20") int tamano) {
        return consulta.buscar(usuario, tipo, resultado, estudio, desde, hasta, pagina, tamano);
    }
}
