package pe.edu.utec.vetcare.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.RegularizacionAtencionEntity;
import java.util.*;
public interface RegularizacionAtencionRepository extends JpaRepository<RegularizacionAtencionEntity,Long> {
 @org.springframework.data.jpa.repository.Query("select r.cita.idCita from RegularizacionAtencionEntity r where r.idRegularizacion=:id")
 Optional<Long> citaId(@org.springframework.data.repository.query.Param("id") Long id);
 Optional<RegularizacionAtencionEntity> findFirstByCita_IdCitaOrderByIdRegularizacionDesc(Long idCita);
 List<RegularizacionAtencionEntity> findAllByOrderByFechaSolicitudDescIdRegularizacionDesc();
 List<RegularizacionAtencionEntity> findByIdVeterinarioOrderByFechaSolicitudDescIdRegularizacionDesc(Long idVeterinario);
}
