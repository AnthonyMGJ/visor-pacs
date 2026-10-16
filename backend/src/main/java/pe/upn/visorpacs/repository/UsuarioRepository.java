package pe.upn.visorpacs.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.upn.visorpacs.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPacienteId(Integer idPaciente);
}
