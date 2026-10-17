package pe.upn.visorpacs.auditoria;

import java.time.Instant;
import java.time.ZoneOffset;

public record EventoDto(long id, Instant fecha, String tipo, String resultado, Integer idUsuario,
                        String username, String rol, String estudio, String ip, String detalle) {

    public static EventoDto de(EventoAuditoria e) {
        return new EventoDto(e.getId(), e.getFecha().toInstant(ZoneOffset.UTC), e.getTipo(), e.getResultado(),
                e.getIdUsuario(), e.getUsername(), e.getRol(), e.getEstudio(), e.getIp(), e.getDetalle());
    }
}
