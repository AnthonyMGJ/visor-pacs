package pe.upn.visorpacs.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upn.visorpacs.auditoria.AuditoriaService;
import pe.upn.visorpacs.auditoria.ResultadoEvento;
import pe.upn.visorpacs.auditoria.TipoEvento;
import pe.upn.visorpacs.config.AppProperties;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.UsuarioRepository;
import pe.upn.visorpacs.sesion.EstadoCuentaCache;
import pe.upn.visorpacs.sesion.RevocacionTokens;
import pe.upn.visorpacs.sesion.SesionDuracionProperties;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;
import pe.upn.visorpacs.usuario.UsuarioService;

/**
 * HU01 (acceso válido, rechazo genérico, bloqueo temporal) y HU12 (cierre de sesión, renovación y
 * revocación). Cada inicio de sesión, exitoso o fallido, queda en la auditoría (HU10, CA10.2).
 */
@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;
    private final Clock clock;
    private final AuditoriaService auditoria;
    private final EstadoCuentaCache cache;
    private final RevocacionTokens revocaciones;
    private final SesionDuracionProperties duracion;
    private final String dummyHash; // para gastar el mismo tiempo cuando el usuario no existe

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AppProperties props, Clock clock,
                       AuditoriaService auditoria, EstadoCuentaCache cache,
                       RevocacionTokens revocaciones, SesionDuracionProperties duracion) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
        this.clock = clock;
        this.auditoria = auditoria;
        this.cache = cache;
        this.revocaciones = revocaciones;
        this.duracion = duracion;
        this.dummyHash = passwordEncoder.encode("relleno-no-es-una-clave-real");
    }

    // Las excepciones NO deben revertir la transacción: el contador de intentos debe guardarse.
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public IssuedToken login(String username, String rawPassword) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Usuario usuario = usuarios.findByUsername(username).orElse(null);

        if (usuario == null || !usuario.isActivo()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            fallo(usuario, username, usuario == null ? "usuario inexistente" : "cuenta inactiva");
            throw new InvalidCredentialsException();
        }
        if (usuario.estaBloqueado(ahora)) {
            fallo(usuario, username, "cuenta bloqueada");
            throw new AccountLockedException();
        }
        if (!passwordEncoder.matches(rawPassword, usuario.getPassword())) {
            usuario.registrarIntentoFallido(props.security().maxFailedAttempts(),
                    Duration.ofMinutes(props.security().lockMinutes()), ahora);
            usuarios.save(usuario);
            fallo(usuario, username, usuario.estaBloqueado(ahora) ? "clave incorrecta; cuenta bloqueada" : "clave incorrecta");
            throw new InvalidCredentialsException();
        }
        if (!usuario.habilitada()) { // CA08.3: sin permisos no entra, y el mensaje no lo revela
            fallo(usuario, username, "sin permiso de acceso");
            throw new InvalidCredentialsException();
        }
        usuario.registrarAccesoExitoso();
        usuarios.save(usuario);
        auditoria.registrar(TipoEvento.INICIO_SESION, ResultadoEvento.EXITO, usuario, null, null);
        return jwtService.issue(usuario);
    }

    /**
     * Emite un token nuevo para quien sigue activo. El frontend lo pide mientras hay actividad (HU12).
     * La sesión tiene un tope absoluto: pasadas las horas configuradas desde el inicio de sesión con clave,
     * ya no se renueva y la persona debe volver a escribir su clave.
     */
    @Transactional(readOnly = true)
    public IssuedToken renovar(int idUsuario, Instant inicioSesion) {
        Instant limite = inicioSesion.plus(Duration.ofHours(duracion.duracionMaximaHoras()));
        if (!clock.instant().isBefore(limite)) {
            throw new SesionNoVigenteException();
        }
        Usuario usuario = usuarios.findById(idUsuario).orElseThrow(SesionNoVigenteException::new);
        if (!usuario.habilitada()) {
            throw new SesionNoVigenteException();
        }
        return jwtService.issue(usuario, inicioSesion);
    }

    /**
     * Cierra la sesión en el servidor: sube la versión de sesión, así que los tokens emitidos hasta ahora
     * para esta cuenta (también en otros dispositivos) dejan de valer en su siguiente solicitud.
     */
    @Transactional
    public void cerrarSesion(int idUsuario) {
        Usuario usuario = usuarios.findById(idUsuario).orElseThrow(SesionNoVigenteException::new);
        usuario.invalidarSesiones();
        usuarios.save(usuario);
        cache.invalidarTrasCommit(idUsuario);
        auditoria.registrar(TipoEvento.CIERRE_SESION, ResultadoEvento.EXITO, usuario, null, null);
    }

    /** Cierra solo este token (este dispositivo); los demás tokens de la cuenta siguen valiendo. */
    @Transactional
    public void cerrarEstaSesion(int idUsuario, String jti, Instant expiraEn) {
        Usuario usuario = usuarios.findById(idUsuario).orElseThrow(SesionNoVigenteException::new);
        revocaciones.revocar(jti, idUsuario, expiraEn);
        auditoria.registrar(TipoEvento.CIERRE_SESION, ResultadoEvento.EXITO, usuario, null, "solo este dispositivo");
    }

    /**
     * La persona cambia su propia clave. Exige la actual (un token robado no basta), cierra todas las sesiones
     * anteriores y devuelve un token nuevo para que siga trabajando. Los errores de la clave actual suman
     * intentos fallidos igual que en el inicio de sesión.
     */
    @Transactional(noRollbackFor = {ClaveActualIncorrectaException.class, AccountLockedException.class})
    public IssuedToken cambiarClave(int idUsuario, String claveActual, String claveNueva) {
        Usuario usuario = usuarios.findById(idUsuario).orElseThrow(SesionNoVigenteException::new);
        LocalDateTime ahora = LocalDateTime.now(clock);
        if (usuario.estaBloqueado(ahora)) {
            throw new AccountLockedException();
        }
        if (!passwordEncoder.matches(claveActual, usuario.getPassword())) {
            usuario.registrarIntentoFallido(props.security().maxFailedAttempts(),
                    Duration.ofMinutes(props.security().lockMinutes()), ahora);
            usuarios.save(usuario);
            auditoria.registrar(TipoEvento.CLAVE_CAMBIADA, ResultadoEvento.FALLO, usuario, null, "clave actual incorrecta");
            throw new ClaveActualIncorrectaException();
        }
        if (claveNueva == null || claveNueva.length() < UsuarioService.CLAVE_MINIMA) {
            throw new UsuarioInvalidoException("La clave nueva debe tener al menos " + UsuarioService.CLAVE_MINIMA + " caracteres.");
        }
        if (claveNueva.equals(claveActual)) {
            throw new UsuarioInvalidoException("La clave nueva debe ser distinta de la actual.");
        }
        usuario.registrarAccesoExitoso();
        usuario.cambiarClave(passwordEncoder.encode(claveNueva)); // sube la versión de sesión
        usuarios.save(usuario);
        cache.invalidarTrasCommit(idUsuario);
        auditoria.registrar(TipoEvento.CLAVE_CAMBIADA, ResultadoEvento.EXITO, usuario, null, null);
        return jwtService.issue(usuario);
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfil(int idUsuario) {
        Usuario u = usuarios.findById(idUsuario).orElseThrow(SesionNoVigenteException::new);
        List<String> permisos = u.getPermisos().stream().map(Permiso::getCodigo).sorted().toList();
        return new PerfilResponse(u.getUsername(), u.getNombres() + " " + u.getApellidos(),
                u.getRol().getNombre(), permisos);
    }

    private void fallo(Usuario usuario, String usernameIntentado, String motivo) {
        if (usuario != null) {
            auditoria.registrar(TipoEvento.INICIO_SESION, ResultadoEvento.FALLO, usuario, null, motivo);
        } else {
            auditoria.registrar(TipoEvento.INICIO_SESION, ResultadoEvento.FALLO, null, usernameIntentado,
                    null, null, motivo);
        }
    }
}
