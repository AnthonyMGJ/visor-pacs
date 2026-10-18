package pe.upn.visorpacs.recuperacion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlmacenRecuperacionJdbc implements AlmacenRecuperacion {

    private final JdbcTemplate jdbc;

    public AlmacenRecuperacionJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(int idUsuario, String tokenHash, Instant creadoEn, Instant venceEn, String ip) {
        jdbc.update("INSERT INTO recuperacion_clave (id_usuario, token_hash, creado_en, vence_en, ip) VALUES (?, ?, ?, ?, ?)",
                idUsuario, tokenHash, Timestamp.from(creadoEn), Timestamp.from(venceEn), ip);
    }

    @Override
    public int contarDesde(int idUsuario, Instant desde) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recuperacion_clave WHERE id_usuario = ? AND creado_en >= ?",
                Integer.class, idUsuario, Timestamp.from(desde));
        return n == null ? 0 : n;
    }

    @Override
    public Optional<Registro> buscar(String tokenHash) {
        List<Registro> filas = jdbc.query(
                "SELECT id_usuario, vence_en, usado_en FROM recuperacion_clave WHERE token_hash = ?",
                (rs, i) -> new Registro(rs.getInt("id_usuario"), rs.getTimestamp("vence_en").toInstant(),
                        rs.getTimestamp("usado_en") == null ? null : rs.getTimestamp("usado_en").toInstant()),
                tokenHash);
        return filas.stream().findFirst();
    }

    @Override
    public boolean consumir(String tokenHash, Instant ahora) {
        // Una sola sentencia: dos solicitudes simultáneas con el mismo enlace no pueden ganar las dos.
        return jdbc.update("UPDATE recuperacion_clave SET usado_en = ? WHERE token_hash = ? AND usado_en IS NULL AND vence_en > ?",
                Timestamp.from(ahora), tokenHash, Timestamp.from(ahora)) == 1;
    }

    @Override
    public int anularPendientes(int idUsuario, Instant ahora) {
        return jdbc.update("UPDATE recuperacion_clave SET usado_en = ? WHERE id_usuario = ? AND usado_en IS NULL",
                Timestamp.from(ahora), idUsuario);
    }

    @Override
    public void purgarAnteriores(Instant limite) {
        jdbc.update("DELETE FROM recuperacion_clave WHERE vence_en < ?", Timestamp.from(limite));
    }
}
