package pe.upn.visorpacs.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "permiso")
public class Permiso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso")
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(length = 150)
    private String descripcion;

    protected Permiso() {}

    public Permiso(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Permiso otro && codigo != null && codigo.equals(otro.codigo);
    }

    @Override
    public int hashCode() {
        return codigo == null ? 0 : codigo.hashCode();
    }
}
