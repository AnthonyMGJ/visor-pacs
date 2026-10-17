package pe.upn.visorpacs.sesion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlmacenRevocacionesJdbc implements AlmacenRevocaciones {

    private final JdbcTemplate jdbc;

    public AlmacenRevocacionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(String jti, int idUsuario, Instant expiraEn, Instant revocadoEn) {
        // INSERT IGNORE: revocar dos veces el mismo token no es un error.
        jdbc.update("INSERT IGNORE INTO token_revocado (jti, id_usuario, expira_en, revocado_en) VALUES (?, ?, ?, ?)",
                jti, idUsuario, Timestamp.from(expiraEn), Timestamp.from(revocadoEn));
    }

    @Override
    public Map<String, Instant> cargarVigentes(Instant ahora) {
        Map<String, Instant> mapa = new HashMap<>();
        jdbc.query("SELECT jti, expira_en FROM token_revocado WHERE expira_en > ?",
                rs -> {
                    mapa.put(rs.getString("jti"), rs.getTimestamp("expira_en").toInstant());
                }, Timestamp.from(ahora));
        return mapa;
    }

    @Override
    public void purgarVencidos(Instant ahora) {
        jdbc.update("DELETE FROM token_revocado WHERE expira_en <= ?", Timestamp.from(ahora));
    }
}
