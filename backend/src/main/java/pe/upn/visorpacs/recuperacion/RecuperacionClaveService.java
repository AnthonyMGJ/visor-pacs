package pe.upn.visorpacs.recuperacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.upn.visorpacs.auditoria.AuditoriaService;
import pe.upn.visorpacs.auditoria.ResultadoEvento;
import pe.upn.visorpacs.auditoria.TipoEvento;
import pe.upn.visorpacs.domain.Roles;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.UsuarioRepository;
import pe.upn.visorpacs.sesion.EstadoCuentaCache;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;
import pe.upn.visorpacs.usuario.UsuarioService;
import pe.upn.visorpacs.web.RecursoNoEncontradoException;

/**
 * RF-09: recuperación de clave por correo para MEDICO y PACIENTE (el ADMIN queda fuera: su clave la restablece
 * otro ADMIN). El token es aleatorio de 256 bits, vale una sola vez, vence a los pocos minutos y solo se guarda
 * su hash. La solicitud responde igual exista o no la cuenta; el correo sale por un hilo aparte.
 */
@Service
public class RecuperacionClaveService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Duration VENTANA_CUENTA = Duration.ofHours(1);
    private static final Duration RETENCION = Duration.ofDays(1);

    private final UsuarioRepository usuarios;
    private final AlmacenRecuperacion almacen;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final EstadoCuentaCache cache;
    private final EnviadorCorreo correo;
    private final RecuperacionProperties props;
    private final Clock clock;

    public RecuperacionClaveService(UsuarioRepository usuarios, AlmacenRecuperacion almacen, PasswordEncoder encoder,
                                    AuditoriaService auditoria, EstadoCuentaCache cache, EnviadorCorreo correo,
                                    RecuperacionProperties props, Clock clock) {
        this.usuarios = usuarios;
        this.almacen = almacen;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.cache = cache;
        this.correo = correo;
        this.props = props;
        this.clock = clock;
    }

    /** Pide el enlace. Nunca revela si el correo existe: todos los caminos terminan igual para quien llama. */
    public void solicitar(String email, String ip) {
        exigirHabilitada();
        Instant ahora = clock.instant();
        Optional<Usuario> encontrado = usuarios.findByEmail(email);

        if (encontrado.isEmpty()) {
            almacen.contarDesde(-1, ahora.minus(VENTANA_CUENTA)); // misma consulta que el camino real (tiempo parecido)
            auditoria.registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.FALLO,
                    null, null, null, null, "correo no registrado: " + enmascarar(email));
            return;
        }
        Usuario u = encontrado.get();
        String motivo = motivoNoElegible(u);
        if (motivo != null) {
            almacen.contarDesde(u.getId(), ahora.minus(VENTANA_CUENTA));
            auditoria.registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.FALLO, u, null, motivo);
            return;
        }
        if (props.maxPorCuentaHora() > 0
                && almacen.contarDesde(u.getId(), ahora.minus(VENTANA_CUENTA)) >= props.maxPorCuentaHora()) {
            auditoria.registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.FALLO, u, null,
                    "límite por cuenta superado");
            return;
        }

        almacen.anularPendientes(u.getId(), ahora); // solo vale el enlace más reciente
        String token = nuevoToken();
        almacen.guardar(u.getId(), hash(token), ahora, ahora.plus(Duration.ofMinutes(props.vigenciaMinutos())), ip);
        almacen.purgarAnteriores(ahora.minus(RETENCION));
        auditoria.registrar(TipoEvento.RECUPERACION_SOLICITADA, ResultadoEvento.EXITO, u, null, null);
        correo.enviar(u.getEmail(), "Restablecer tu clave del Visor PACS", textoEnlace(u, token));
    }

    /**
     * Fija la clave nueva con el token. Cierra todas las sesiones de la cuenta, anula otros enlaces pendientes y
     * levanta un bloqueo por intentos. No inicia sesión.
     */
    @Transactional(noRollbackFor = {TokenRecuperacionInvalidoException.class, UsuarioInvalidoException.class})
    public void restablecer(String token, String claveNueva, String ip) {
        exigirHabilitada();
        Instant ahora = clock.instant();
        String hash = hash(token);

        AlmacenRecuperacion.Registro registro = almacen.buscar(hash).orElse(null);
        if (registro == null || registro.usadoEn() != null || !registro.venceEn().isAfter(ahora)) {
            fallido(registro == null ? null : usuarios.findById(registro.idUsuario()).orElse(null),
                    registro == null ? "enlace desconocido" : registro.usadoEn() != null ? "enlace ya usado" : "enlace vencido");
            throw new TokenRecuperacionInvalidoException();
        }
        Usuario u = usuarios.findById(registro.idUsuario()).orElse(null);
        if (u == null || motivoNoElegible(u) != null) {
            fallido(u, "cuenta no elegible");
            throw new TokenRecuperacionInvalidoException();
        }
        // La política se valida antes de gastar el enlace: si la clave es débil, puede intentar otra con el mismo.
        if (claveNueva == null || claveNueva.length() < UsuarioService.CLAVE_MINIMA) {
            fallido(u, "clave nueva no cumple la política");
            throw new UsuarioInvalidoException("La clave debe tener al menos " + UsuarioService.CLAVE_MINIMA + " caracteres.");
        }
        if (encoder.matches(claveNueva, u.getPassword())) {
            fallido(u, "clave nueva igual a la actual");
            throw new UsuarioInvalidoException("La clave nueva debe ser distinta de la actual.");
        }
        if (!almacen.consumir(hash, ahora)) { // otra solicitud lo usó justo antes
            fallido(u, "enlace ya usado");
            throw new TokenRecuperacionInvalidoException();
        }

        u.registrarAccesoExitoso();                       // levanta el bloqueo por intentos fallidos
        u.cambiarClave(encoder.encode(claveNueva));       // sube la versión de sesión: cierra todas las sesiones
        usuarios.save(u);
        almacen.anularPendientes(u.getId(), ahora);
        cache.invalidarTrasCommit(u.getId());
        auditoria.registrar(TipoEvento.RECUPERACION_COMPLETADA, ResultadoEvento.EXITO, u, null, null);

        despuesDelCommit(() -> {
            correo.enviar(u.getEmail(), "Tu clave del Visor PACS fue cambiada", textoClaveCambiada(u));
            if (Roles.MEDICO.equals(u.getRol().getNombre())) {
                avisarAdministradores(u);
            }
        });
    }

    private void avisarAdministradores(Usuario medico) {
        List<Usuario> admins = usuarios.findByRolNombreAndActivoTrue(Roles.ADMIN);
        String texto = "El médico " + medico.getNombres() + " " + medico.getApellidos() + " (usuario "
                + medico.getUsername() + ") restableció su clave con el enlace enviado a su correo.\n"
                + "Si no corresponde a una solicitud suya, desactive la cuenta y revise la auditoría.";
        for (Usuario admin : admins) {
            correo.enviar(admin.getEmail(), "Aviso: un médico restableció su clave", texto);
        }
    }

    private void fallido(Usuario u, String motivo) {
        auditoria.registrar(TipoEvento.RECUPERACION_FALLIDA, ResultadoEvento.FALLO, u, null, motivo);
    }

    private void exigirHabilitada() {
        if (!props.habilitada()) {
            throw new RecursoNoEncontradoException("Recurso no encontrado.");
        }
    }

    /** null = puede recuperar. ADMIN, cuenta inactiva o médico sin permiso de acceso no reciben correo. */
    private static String motivoNoElegible(Usuario u) {
        if (Roles.ADMIN.equals(u.getRol().getNombre())) {
            return "rol ADMIN excluido";
        }
        if (!u.isActivo()) {
            return "cuenta inactiva";
        }
        if (!u.habilitada()) {
            return "sin permiso de acceso";
        }
        return null;
    }

    private String textoEnlace(Usuario u, String token) {
        String base = props.frontendUrl().replaceAll("/+$", "");
        return "Hola " + u.getNombres() + ",\n\n"
                + "Recibimos una solicitud para restablecer la clave de tu cuenta del Visor PACS.\n"
                + "Abre este enlace (vale " + props.vigenciaMinutos() + " minutos y solo una vez):\n\n"
                + base + "/nueva-clave#token=" + token + "\n\n"
                + "Si no lo pediste tú, ignora este correo: tu clave actual sigue igual.\n"
                + "Nadie del equipo te pedirá este enlace ni tu clave.\n";
    }

    private static String textoClaveCambiada(Usuario u) {
        return "Hola " + u.getNombres() + ",\n\n"
                + "La clave de tu cuenta del Visor PACS fue cambiada y se cerraron tus sesiones abiertas.\n"
                + "Si no fuiste tú, comunícate de inmediato con el administrador.\n";
    }

    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    static String nuevoToken() {
        byte[] bytes = new byte[32]; // 256 bits
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 existe en toda JVM
        }
    }

    /** "ana.perez@dominio.pe" -> "a***@dominio.pe": suficiente para la auditoría sin guardar el correo completo. */
    static String enmascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
