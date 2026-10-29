package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.HistoriaPublicacionEntity;
import java.util.List;

public interface HistoriaPublicacionRepository extends JpaRepository<HistoriaPublicacionEntity,Long> {
    List<HistoriaPublicacionEntity> findTop40ByOrderByFechaCreacionDesc();
    List<HistoriaPublicacionEntity> findByMascotaPropietarioUsuarioCorreoOrderByFechaCreacionDesc(String correo);
}
