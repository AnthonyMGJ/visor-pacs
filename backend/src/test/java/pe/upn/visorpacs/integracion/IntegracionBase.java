package pe.upn.visorpacs.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.usuario.UsuarioService;

/**
 * Base de las pruebas de integración: aplicación completa + MySQL 8.4 real (Testcontainers) + Orthanc simulado.
 * Un solo contenedor y un solo contexto de Spring para todas las clases *IT (arranque único).
 * Como la tabla de auditoría no admite borrados, cada prueba crea usuarios con nombres únicos
 * y filtra por ellos en lugar de suponer que la base está vacía.
 */
@SpringBootTest
@AutoConfigureMockMvc
// El límite de inicios de sesión por IP queda prácticamente apagado: todas las pruebas entran desde la misma IP.
// LimiteLoginIT lo vuelve a fijar en un valor bajo para probarlo.
@TestPropertySource(properties = "app.limite-login.max-solicitudes=100000")
abstract class IntegracionBase {

    protected static final String CLAVE = "Clave-de-prueba-123";
    protected static final MediaType JSON = MediaType.APPLICATION_JSON;

    static final MySQLContainer<?> MYSQL;
    static final OrthancSimulado ORTHANC;

    static {
        DockerCompat.aplicar();
        // --log-bin-trust-function-creators: necesario para crear los triggers de la auditoría (V5).
        MYSQL = new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
        MYSQL.start();
        ORTHANC = OrthancSimulado.iniciar();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
        registro.add("spring.datasource.username", MYSQL::getUsername);
        registro.add("spring.datasource.password", MYSQL::getPassword);
        registro.add("app.jwt.secret", () -> "secreto-de-pruebas-de-integracion-0123456789");
        registro.add("app.orthanc.base-url", ORTHANC::url);
        registro.add("app.orthanc.username", () -> "backend");
        registro.add("app.orthanc.password", () -> "clave-orthanc");
        registro.add("app.bootstrap.enabled", () -> "false");
        registro.add("app.security.max-failed-attempts", () -> "5");
        registro.add("app.security.lock-minutes", () -> "15");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UsuarioService usuarios;

    /** Cuenta de prueba ya creada. */
    protected record Cuenta(Integer id, String username) {}

    protected static String unico(String base) {
        return base + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Cuenta medico() {
        String u = unico("medico");
        return cuenta(usuarios.crearMedico(u, CLAVE, "Ana", "Pérez", u + "@clinica.pe"));
    }

    protected Cuenta admin() {
        String u = unico("admin");
        return cuenta(usuarios.crearAdmin(u, CLAVE, "Luz", "Rojas", u + "@clinica.pe"));
    }

    protected Cuenta paciente(String dicomId) {
        String u = unico("paciente");
        return cuenta(usuarios.crearPaciente(u, CLAVE, "Juan", "Díaz", u + "@correo.pe", dicomId));
    }

    private static Cuenta cuenta(Usuario u) {
        return new Cuenta(u.getId(), u.getUsername());
    }

    /** Inicia sesión por la API y devuelve el JWT. */
    protected String login(String username) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String cuerpo(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    protected MockHttpServletRequestBuilder cambiarEstado(int id, boolean activo, String tokenAdmin) throws Exception {
        return patch("/api/admin/usuarios/" + id + "/estado").contentType(JSON)
                .header("Authorization", bearer(tokenAdmin)).content(cuerpo(Map.of("activo", activo)));
    }
}
