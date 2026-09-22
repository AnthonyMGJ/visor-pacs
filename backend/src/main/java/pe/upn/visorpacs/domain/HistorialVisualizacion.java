package pe.upn.visorpacs.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historial_visualizacion")
public class HistorialVisualizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudio", nullable = false)
    private Estudio estudio;

    @Column(name = "fecha_acceso", insertable = false, updatable = false)
    private LocalDateTime fechaAcceso;

    protected HistorialVisualizacion() {}

    public Integer getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Estudio getEstudio() { return estudio; }
    public LocalDateTime getFechaAcceso() { return fechaAcceso; }
}
