package pe.upn.visorpacs.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upn.visorpacs.domain.Estudio;

public interface EstudioRepository extends JpaRepository<Estudio, Integer> {
    Optional<Estudio> findByDicomEstudioId(String dicomEstudioId);
}
