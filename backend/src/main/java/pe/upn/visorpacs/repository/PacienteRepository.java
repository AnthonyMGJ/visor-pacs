package pe.upn.visorpacs.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upn.visorpacs.domain.Paciente;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {
    Optional<Paciente> findByDicomPacienteId(String dicomPacienteId);
}
