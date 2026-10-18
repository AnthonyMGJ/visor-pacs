package pe.upn.visorpacs.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

/**
 * Las migraciones V4/V5/V6/V7 sobre una base que YA tenía datos (la del Sprint 1): los médicos existentes deben
 * conservar su acceso, y los triggers de la auditoría deben crearse en MySQL 8.4. Usa su propio contenedor.
 */
class MigracionIT {

    private static MySQLContainer<?> mysql;

    @BeforeAll
    static void iniciar() {
        DockerCompat.aplicar();
        mysql = new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
        mysql.start();
    }

    @AfterAll
    static void detener() {
        if (mysql != null) mysql.stop();
    }

    private Flyway flyway(String objetivo) {
        var cfg = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration");
        if (objetivo != null) cfg = cfg.target(objetivo);
        return cfg.load();
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    @Test
    void losMedicosDelSprint1ConservanSuAccesoTrasMigrarYSeCreaElRolAdmin() throws Exception {
        flyway("3").migrate(); // el esquema como lo dejó el Sprint 1
        try (Connection c = conexion(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO usuario (username, password, nombres, apellidos, email, id_rol) "
                    + "SELECT 'medico.previo', 'hash', 'Ana', 'Pérez', 'previo@clinica.pe', id_rol FROM rol WHERE nombre_rol = 'MEDICO'");
            s.executeUpdate("INSERT INTO usuario (username, password, nombres, apellidos, email, id_rol) "
                    + "SELECT 'paciente.previo', 'hash', 'Juan', 'Díaz', 'pprevio@correo.pe', id_rol FROM rol WHERE nombre_rol = 'PACIENTE'");
        }

        flyway(null).migrate(); // V4, V5, V6 y V7

        try (Connection c = conexion(); Statement s = c.createStatement()) {
            assertEquals(1, contar(s, "SELECT COUNT(*) FROM rol WHERE nombre_rol = 'ADMIN'"));
            assertEquals(1, contar(s, "SELECT COUNT(*) FROM usuario_permiso up JOIN usuario u ON u.id_usuario = up.id_usuario "
                    + "JOIN permiso p ON p.id_permiso = up.id_permiso WHERE u.username = 'medico.previo' AND p.codigo = 'ACCESO_ESTUDIOS'"));
            assertEquals(0, contar(s, "SELECT COUNT(*) FROM usuario_permiso up JOIN usuario u ON u.id_usuario = up.id_usuario "
                    + "WHERE u.username = 'paciente.previo'"));
            assertEquals(0, contar(s, "SELECT sesion_version FROM usuario WHERE username = 'medico.previo'"));
            // V6: las cuentas existentes no quedan obligadas a cambiar la clave y la tabla de revocaciones existe
            assertEquals(0, contar(s, "SELECT debe_cambiar_clave FROM usuario WHERE username = 'medico.previo'"));
            assertEquals(0, contar(s, "SELECT COUNT(*) FROM token_revocado"));
            // V7 (RF-09): tabla nueva y vacía; las cuentas existentes no cambian
            assertEquals(0, contar(s, "SELECT COUNT(*) FROM recuperacion_clave"));

            s.executeUpdate("INSERT INTO evento_auditoria (fecha_evento, tipo, resultado) VALUES (NOW(3), 'INICIO_SESION', 'EXITO')");
            SQLException ex = assertThrows(SQLException.class, () -> s.executeUpdate("UPDATE evento_auditoria SET tipo = 'X'"));
            assertTrue(ex.getMessage().contains("solo insercion"));
            ex = assertThrows(SQLException.class, () -> s.executeUpdate("DELETE FROM evento_auditoria"));
            assertTrue(ex.getMessage().contains("solo insercion"));
        }
    }

    private static int contar(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
