package pe.upn.visorpacs.admin;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upn.visorpacs.auditoria.AuditoriaService;
import pe.upn.visorpacs.auditoria.ResultadoEvento;
import pe.upn.visorpacs.auditoria.TipoEvento;
import pe.upn.visorpacs.domain.Permiso;
import pe.upn.visorpacs.domain.Roles;
import pe.upn.visorpacs.domain.Usuario;
import pe.upn.visorpacs.repository.PermisoRepository;
import pe.upn.visorpacs.repository.UsuarioRepository;
import pe.upn.visorpacs.sesion.EstadoCuentaCache;
import pe.upn.visorpacs.usuario.UsuarioDuplicadoException;
import pe.upn.visorpacs.usuario.UsuarioInvalidoException;
import pe.upn.visorpacs.usuario.UsuarioService;
import pe.upn.visorpacs.web.OperacionNoPermitidaException;
import pe.upn.visorpacs.web.PaginaDto;
import pe.upn.visorpacs.web.RecursoNoEncontradoException;

/**
 * HU08 y HU12 (lado servidor): registrar, editar, desactivar y asignar permisos. Cada cambio queda en la
 * auditoría y los que afectan al acceso invalidan de inmediato la memoria de estado de la cuenta,
 * de modo que la siguiente solicitud del afectado ya se evalúa con el estado nuevo.
 */
@Service
public class AdminUsuarioService {

    private final UsuarioRepository usuarios;
    private final PermisoRepository permisos;
    private final UsuarioService usuarioService;
    private final EstadoCuentaCache cache;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public AdminUsuarioService(UsuarioRepository usuarios, PermisoRepository permisos, UsuarioService usuarioService,
                               EstadoCuentaCache cache, AuditoriaService auditoria, Clock clock) {
        this.usuarios = usuarios;
        this.permisos = permisos;
        this.usuarioService = usuarioService;
        this.cache = cache;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaDto<UsuarioDto> listar(String rol, Boolean activo, String texto, int pagina, int tamano) {
        Specification<Usuario> filtro = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (rol != null && !rol.isBlank()) {
                p.add(cb.equal(root.get("rol").get("nombre"), rol.trim().toUpperCase(Locale.ROOT)));
            }
            if (activo != null) {
                p.add(cb.equal(root.get("activo"), activo));
            }
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + escapar(texto.trim().toLowerCase(Locale.ROOT)) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("username")), patron, '\\'),
                        cb.like(cb.lower(root.get("nombres")), patron, '\\'),
                        cb.like(cb.lower(root.get("apellidos")), patron, '\\'),
                        cb.like(cb.lower(root.get("email")), patron, '\\')));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<Usuario> resultado = usuarios.findAll(filtro,
                PaginaDto.pedido(pagina, tamano, Sort.by("username").ascending()));
        LocalDateTime ahora = LocalDateTime.now(clock);
        return PaginaDto.de(resultado, u -> UsuarioDto.de(u, ahora));
    }

    @Transactional(readOnly = true)
    public UsuarioDto obtener(int id) {
        return dto(cargar(id));
    }

    @Transactional(readOnly = true)
    public List<PermisoDto> catalogoDePermisos() {
        return permisos.findAll(Sort.by("codigo")).stream().map(PermisoDto::de).toList();
    }

    @Transactional
    public UsuarioDto crear(ActorAdmin actor, CrearUsuarioRequest r) {
        String rol = r.rol() == null ? "" : r.rol().trim().toUpperCase(Locale.ROOT);
        if (!Roles.MEDICO.equals(rol) && !Roles.PACIENTE.equals(rol)) {
            throw new UsuarioInvalidoException("El rol debe ser MEDICO o PACIENTE.");
        }
        Usuario nuevo = usuarioService.crearCuenta(rol, r.username(), r.clave(), r.nombres(), r.apellidos(),
                r.email(), r.dicomPacienteId());
        registrar(TipoEvento.USUARIO_CREADO, actor, "cuenta=" + nuevo.getUsername() + " rol=" + rol);
        return dto(nuevo);
    }

    @Transactional
    public UsuarioDto editar(ActorAdmin actor, int id, EditarUsuarioRequest r) {
        Usuario u = cargar(id);
        String nombres = UsuarioService.limpio(r.nombres(), "Los nombres", 100);
        String apellidos = UsuarioService.limpio(r.apellidos(), "Los apellidos", 100);
        String correo = UsuarioService.limpio(r.email(), "El correo", 100);
        UsuarioService.validarCorreo(correo);
        if (usuarios.existsByEmailAndIdNot(correo, id)) {
            throw new UsuarioDuplicadoException("Ya existe un usuario con ese correo.");
        }
        u.actualizarDatos(nombres, apellidos, correo);
        usuarios.save(u);
        registrar(TipoEvento.USUARIO_EDITADO, actor, "cuenta=" + u.getUsername());
        return dto(u);
    }

    /**
     * Desactivar revoca el acceso en la siguiente solicitud (CA12.2): sube la versión de sesión y
     * la cuenta deja de ser válida aunque su token no haya vencido.
     */
    @Transactional
    public UsuarioDto cambiarEstado(ActorAdmin actor, int id, boolean activo) {
        Usuario u = cargar(id);
        if (!activo && u.getId() != null && u.getId() == actor.id()) {
            throw new OperacionNoPermitidaException("No puedes desactivar tu propia cuenta.");
        }
        if (u.isActivo() == activo) {
            return dto(u); // sin cambios: no se audita ni se invalida nada
        }
        u.setActivo(activo);
        if (activo) {
            u.registrarAccesoExitoso(); // al reactivar, parte sin bloqueos ni intentos fallidos
        } else {
            u.invalidarSesiones();
        }
        usuarios.save(u);
        cache.invalidarTrasCommit(id);
        registrar(activo ? TipoEvento.USUARIO_ACTIVADO : TipoEvento.USUARIO_DESACTIVADO, actor,
                "cuenta=" + u.getUsername());
        return dto(u);
    }

    /**
     * Para quien olvidó su clave: el ADMIN genera una clave temporal (se entrega en persona o por un canal seguro).
     * La cuenta queda con sesiones cerradas, sin bloqueo y obligada a cambiar la clave en su siguiente acceso.
     */
    @Transactional
    public ClaveTemporalDto restablecerClave(ActorAdmin actor, int id) {
        Usuario u = cargar(id);
        if (u.getId() != null && u.getId() == actor.id()) {
            throw new OperacionNoPermitidaException("Para cambiar tu propia clave usa la opción de cambiar clave.");
        }
        String temporal = usuarioService.restablecerClave(u);
        cache.invalidarTrasCommit(id);
        registrar(TipoEvento.CLAVE_RESTABLECIDA, actor, "cuenta=" + u.getUsername());
        return new ClaveTemporalDto(temporal);
    }

    @Transactional
    public UsuarioDto otorgarPermiso(ActorAdmin actor, int id, String codigo) {
        Usuario u = cargar(id);
        Permiso permiso = permisoExistente(codigo);
        exigirMedico(u);
        if (!u.tienePermiso(permiso.getCodigo())) {
            u.otorgar(permiso);
            usuarios.save(u);
            cache.invalidarTrasCommit(id);
            registrar(TipoEvento.PERMISO_OTORGADO, actor, "cuenta=" + u.getUsername() + " permiso=" + permiso.getCodigo());
        }
        return dto(u);
    }

    @Transactional
    public UsuarioDto revocarPermiso(ActorAdmin actor, int id, String codigo) {
        Usuario u = cargar(id);
        Permiso permiso = permisoExistente(codigo);
        exigirMedico(u);
        if (u.revocar(permiso.getCodigo())) {
            usuarios.save(u);
            cache.invalidarTrasCommit(id);
            registrar(TipoEvento.PERMISO_REVOCADO, actor, "cuenta=" + u.getUsername() + " permiso=" + permiso.getCodigo());
        }
        return dto(u);
    }

    private Usuario cargar(int id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
    }

    private Permiso permisoExistente(String codigo) {
        String normalizado = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        return permisos.findByCodigo(normalizado)
                .orElseThrow(() -> new RecursoNoEncontradoException("El permiso no existe."));
    }

    /** Hoy el único permiso es de acceso a estudios y solo tiene sentido para médicos (el paciente se rige por su PatientID). */
    private static void exigirMedico(Usuario u) {
        if (!Roles.MEDICO.equals(u.getRol().getNombre())) {
            throw new OperacionNoPermitidaException("Los permisos solo se asignan a cuentas de médico.");
        }
    }

    private UsuarioDto dto(Usuario u) {
        return UsuarioDto.de(u, LocalDateTime.now(clock));
    }

    private void registrar(TipoEvento tipo, ActorAdmin actor, String detalle) {
        auditoria.registrar(tipo, ResultadoEvento.EXITO, actor.id(), actor.username(), Roles.ADMIN, null, detalle);
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
