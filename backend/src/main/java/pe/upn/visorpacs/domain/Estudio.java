package pe.upn.visorpacs.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "estudio")
public class Estudio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudio")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "dicom_estudio_id", nullable = false, unique = true, length = 100)
    private String dicomEstudioId; // StudyInstanceUID

    @Column(name = "fecha_estudio", nullable = false)
    private LocalDate fechaEstudio;

    @Column(name = "descripcion_estudio")
    private String descripcion;

    @Column(name = "modalidad", nullable = false, length = 50)
    private String modalidad;

    @Column(name = "institucion", length = 100)
    private String institucion;

    protected Estudio() {}

    public Integer getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public String getDicomEstudioId() { return dicomEstudioId; }
    public LocalDate getFechaEstudio() { return fechaEstudio; }
    public String getDescripcion() { return descripcion; }
    public String getModalidad() { return modalidad; }
    public String getInstitucion() { return institucion; }
}
