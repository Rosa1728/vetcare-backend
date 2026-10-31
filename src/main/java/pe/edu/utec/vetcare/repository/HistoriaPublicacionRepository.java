package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.HistoriaPublicacionEntity;
import java.util.List;

public interface HistoriaPublicacionRepository extends JpaRepository<HistoriaPublicacionEntity,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<HistoriaPublicacionEntity> {
    List<HistoriaPublicacionEntity> findTop40ByOrderByFechaCreacionDesc();
    List<HistoriaPublicacionEntity> findByMascotaPropietarioUsuarioCorreoOrderByFechaCreacionDesc(String correo);
}
