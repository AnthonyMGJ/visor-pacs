package pe.upn.visorpacs.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upn.visorpacs.domain.Permiso;

public interface PermisoRepository extends JpaRepository<Permiso, Integer> {
    Optional<Permiso> findByCodigo(String codigo);
}
