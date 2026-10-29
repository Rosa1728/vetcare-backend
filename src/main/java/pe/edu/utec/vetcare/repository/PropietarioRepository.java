package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.PropietarioEntity;

import java.util.Optional;

public interface PropietarioRepository
        extends JpaRepository<PropietarioEntity, Long> {

    Optional<PropietarioEntity>
    findByUsuarioIdUsuario(Long idUsuario);

    boolean existsByUsuarioIdUsuario(Long idUsuario);
}