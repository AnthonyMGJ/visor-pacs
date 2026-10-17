package pe.upn.visorpacs.auditoria;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Evento de auditoría. Inmutable: sin setters, columnas no actualizables y, en la base de datos,
 * triggers que rechazan UPDATE y DELETE (V5__auditoria_solo_insercion.sql).
 */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento", updatable = false)
    private Long id;

    @Column(name = "fecha_evento", nullable = false, updatable = false)
    private LocalDateTime fecha; // UTC

    @Column(nullable = false, updatable = false, length = 40)
    private String tipo;

    @Column(nullable = false, updatable = false, length = 10)
    private String resultado;

    @Column(name = "id_usuario", updatable = false)
    private Integer idUsuario;

    @Column(updatable = false, length = 50)
    private String username;

    @Column(updatable = false, length = 20)
    private String rol;

    @Column(name = "dicom_estudio_id", updatable = false, length = 100)
    private String estudio;

    @Column(updatable = false, length = 45)
    private String ip;

    @Column(updatable = false, length = 255)
    private String detalle;

    protected EventoAuditoria() {}

    public EventoAuditoria(LocalDateTime fecha, TipoEvento tipo, ResultadoEvento resultado, Integer idUsuario,
                           String username, String rol, String estudio, String ip, String detalle) {
        this.fecha = fecha;
        this.tipo = tipo.name();
        this.resultado = resultado.name();
        this.idUsuario = idUsuario;
        this.username = recortar(username, 50);
        this.rol = recortar(rol, 20);
        this.estudio = recortar(estudio, 100);
        this.ip = recortar(ip, 45);
        this.detalle = recortar(detalle, 255);
    }

    private static String recortar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() > max ? valor.substring(0, max) : valor;
    }

    public Long getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public String getResultado() { return resultado; }
    public Integer getIdUsuario() { return idUsuario; }
    public String getUsername() { return username; }
    public String getRol() { return rol; }
    public String getEstudio() { return estudio; }
    public String getIp() { return ip; }
    public String getDetalle() { return detalle; }
}
