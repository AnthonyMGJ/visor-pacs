package pe.upn.visorpacs.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "paciente")
public class Paciente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paciente")
    private Integer id;

    @Column(name = "dicom_paciente_id", nullable = false, unique = true, length = 100)
    private String dicomPacienteId;

    @Column(name = "nombres_paciente", nullable = false, length = 100)
    private String nombres;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "sexo", length = 1)
    private String sexo;

    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    protected Paciente() {}

    public Integer getId() { return id; }
    public String getDicomPacienteId() { return dicomPacienteId; }
    public String getNombres() { return nombres; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public String getSexo() { return sexo; }
}
