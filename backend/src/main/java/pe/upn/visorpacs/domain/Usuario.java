package pe.upn.visorpacs.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuario")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password; // hash BCrypt, nunca texto plano

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "estado", nullable = false)
    private boolean activo = true;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    /** Solo para usuarios con rol PACIENTE: su registro de paciente (define qué estudios puede ver). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    /** Permisos concedidos (tabla usuario_permiso). Ver {@link Permisos}. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_permiso",
            joinColumns = @JoinColumn(name = "id_usuario"),
            inverseJoinColumns = @JoinColumn(name = "id_permiso"))
    private Set<Permiso> permisos = new HashSet<>();

    /** Sube al cerrar sesión o desactivar la cuenta: los tokens con una versión anterior dejan de valer. */
    @Column(name = "sesion_version", nullable = false)
    private int sesionVersion;

    /** true tras un restablecimiento por el ADMIN: hasta cambiar la clave, el token solo sirve para cambiarla. */
    @Column(name = "debe_cambiar_clave", nullable = false)
    private boolean debeCambiarClave;

    protected Usuario() {}

    public static Usuario nuevo(String username, String passwordHash, String nombres,
                                String apellidos, String email, Rol rol) {
        Usuario u = new Usuario();
        u.username = username;
        u.password = passwordHash;
        u.nombres = nombres;
        u.apellidos = apellidos;
        u.email = email;
        u.rol = rol;
        return u;
    }

    public static Usuario nuevoPaciente(String username, String passwordHash, String nombres,
                                        String apellidos, String email, Rol rol, Paciente paciente) {
        Usuario u = nuevo(username, passwordHash, nombres, apellidos, email, rol);
        u.paciente = paciente;
        return u;
    }

    public boolean estaBloqueado(LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /** Suma un intento fallido; al llegar al máximo bloquea la cuenta y reinicia el contador. */
    public void registrarIntentoFallido(int maxIntentos, Duration bloqueo, LocalDateTime ahora) {
        intentosFallidos++;
        if (intentosFallidos >= maxIntentos) {
            bloqueadoHasta = ahora.plus(bloqueo);
            intentosFallidos = 0;
        }
    }

    public void registrarAccesoExitoso() {
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }

    /** La propia persona cambia su clave: se cierran todas las sesiones anteriores y se quita la marca de clave temporal. */
    public void cambiarClave(String nuevoHash) {
        this.password = nuevoHash;
        this.debeCambiarClave = false;
        invalidarSesiones();
    }

    /** Un ADMIN fija una clave temporal: se cierran las sesiones, se levanta el bloqueo y se exige cambiarla. */
    public void restablecerClave(String hashTemporal) {
        this.password = hashTemporal;
        this.debeCambiarClave = true;
        registrarAccesoExitoso();
        invalidarSesiones();
    }

    public boolean tienePermiso(String codigo) {
        return permisos.stream().anyMatch(p -> p.getCodigo().equals(codigo));
    }

    public void otorgar(Permiso permiso) {
        permisos.add(permiso);
    }

    public boolean revocar(String codigo) {
        return permisos.removeIf(p -> p.getCodigo().equals(codigo));
    }

    /** Invalida todos los tokens emitidos hasta ahora para este usuario. */
    public void invalidarSesiones() {
        sesionVersion++;
    }

    /**
     * ¿Puede esta cuenta iniciar sesión / seguir usándola? Debe estar activa y, si es MEDICO,
     * conservar el permiso de acceso a estudios (CA08.3).
     */
    public boolean habilitada() {
        if (!activo) {
            return false;
        }
        return !Roles.MEDICO.equals(rol.getNombre()) || tienePermiso(Permisos.ACCESO_ESTUDIOS);
    }

    public void actualizarDatos(String nombres, String apellidos, String email) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.email = email;
    }

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getEmail() { return email; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int n) { this.intentosFallidos = n; }
    public LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(LocalDateTime t) { this.bloqueadoHasta = t; }
    public Rol getRol() { return rol; }
    public Paciente getPaciente() { return paciente; }
    public Set<Permiso> getPermisos() { return Collections.unmodifiableSet(permisos); }
    public int getSesionVersion() { return sesionVersion; }
    public boolean isDebeCambiarClave() { return debeCambiarClave; }
}
