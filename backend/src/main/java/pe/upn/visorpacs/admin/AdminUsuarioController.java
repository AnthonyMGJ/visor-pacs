package pe.upn.visorpacs.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.upn.visorpacs.sesion.SesionTokenValidator;
import pe.upn.visorpacs.web.PaginaDto;

/** Gestión de cuentas y permisos. Todo bajo /api/admin/** exige el rol ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administración", description = "Cuentas y permisos (solo rol ADMIN)")
public class AdminUsuarioController {
    private final AdminUsuarioService servicio;

    public AdminUsuarioController(AdminUsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Lista cuentas con filtros y paginación",
            description = "rol: MEDICO|PACIENTE|ADMIN; activo: true|false; q: texto en usuario, nombres, apellidos o correo.")
    public PaginaDto<UsuarioDto> listar(@RequestParam(required = false) String rol,
                                        @RequestParam(required = false) Boolean activo,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(rol, activo, q, pagina, tamano);
    }

    @GetMapping("/usuarios/{id}")
    @Operation(summary = "Detalle de una cuenta")
    public UsuarioDto obtener(@PathVariable int id) {
        return servicio.obtener(id);
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un médico o un paciente",
            description = "Los médicos nacen con el permiso ACCESO_ESTUDIOS. 409 si el usuario o el correo ya existen.")
    public UsuarioDto crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearUsuarioRequest request) {
        return servicio.crear(actor(jwt), request);
    }

    @PutMapping("/usuarios/{id}")
    @Operation(summary = "Edita nombres, apellidos y correo")
    public UsuarioDto editar(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                             @Valid @RequestBody EditarUsuarioRequest request) {
        return servicio.editar(actor(jwt), id, request);
    }

    @PatchMapping("/usuarios/{id}/estado")
    @Operation(summary = "Activa o desactiva una cuenta",
            description = "Al desactivar, la siguiente solicitud del usuario con sesión abierta es rechazada (401). 409 si intentas desactivarte a ti mismo.")
    public UsuarioDto cambiarEstado(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                    @Valid @RequestBody CambiarEstadoRequest request) {
        return servicio.cambiarEstado(actor(jwt), id, request.activo());
    }

    @PostMapping("/usuarios/{id}/restablecer-clave")
    @Operation(summary = "Restablece la clave de una cuenta con una clave temporal",
            description = "Devuelve la clave temporal una sola vez. La cuenta queda obligada a cambiarla al entrar; sus sesiones abiertas se cierran. 409 si intentas restablecer tu propia clave.")
    public ClaveTemporalDto restablecerClave(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return servicio.restablecerClave(actor(jwt), id);
    }

    @GetMapping("/permisos")
    @Operation(summary = "Catálogo de permisos asignables")
    public List<PermisoDto> permisos() {
        return servicio.catalogoDePermisos();
    }

    @PutMapping("/usuarios/{id}/permisos/{codigo}")
    @Operation(summary = "Asigna un permiso a un médico (idempotente)")
    public UsuarioDto otorgar(@AuthenticationPrincipal Jwt jwt, @PathVariable int id, @PathVariable String codigo) {
        return servicio.otorgarPermiso(actor(jwt), id, codigo);
    }

    @DeleteMapping("/usuarios/{id}/permisos/{codigo}")
    @Operation(summary = "Revoca un permiso de un médico (idempotente)",
            description = "Un médico sin ACCESO_ESTUDIOS recibe 403 al pedir imágenes y no puede iniciar sesión.")
    public UsuarioDto revocar(@AuthenticationPrincipal Jwt jwt, @PathVariable int id, @PathVariable String codigo) {
        return servicio.revocarPermiso(actor(jwt), id, codigo);
    }

    private static ActorAdmin actor(Jwt jwt) {
        int id = ((Number) jwt.getClaims().get(SesionTokenValidator.CLAIM_USUARIO)).intValue();
        return new ActorAdmin(id, jwt.getSubject());
    }
}
