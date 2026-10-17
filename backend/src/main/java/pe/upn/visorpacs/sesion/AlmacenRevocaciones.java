package pe.upn.visorpacs.sesion;

import java.time.Instant;
import java.util.Map;

/** Dónde se guardan los tokens revocados de forma individual (la tabla token_revocado). */
public interface AlmacenRevocaciones {
    void guardar(String jti, int idUsuario, Instant expiraEn, Instant revocadoEn);

    /** jti -> vencimiento, solo de los tokens que todavía no vencieron. */
    Map<String, Instant> cargarVigentes(Instant ahora);

    void purgarVencidos(Instant ahora);
}
