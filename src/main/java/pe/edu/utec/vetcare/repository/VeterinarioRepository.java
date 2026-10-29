package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.VeterinarioEntity;

public interface VeterinarioRepository extends JpaRepository<VeterinarioEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from VeterinarioEntity v where v.idVeterinario=:id")
    java.util.Optional<VeterinarioEntity> bloquearAgenda(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsByUsuario_IdUsuario(Long idUsuario);
    java.util.Optional<VeterinarioEntity> findByUsuario_IdUsuario(Long idUsuario);
    java.util.Optional<VeterinarioEntity> findByUsuario_Correo(String correo);
    boolean existsByNroColegiatura(String nroColegiatura);
}