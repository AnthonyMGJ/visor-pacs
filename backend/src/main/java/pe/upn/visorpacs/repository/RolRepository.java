package pe.upn.visorpacs.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upn.visorpacs.domain.Rol;

public interface RolRepository extends JpaRepository<Rol, Integer> {
    Optional<Rol> findByNombre(String nombre);
}
