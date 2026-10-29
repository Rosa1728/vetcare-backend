package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.AtencionEntity;

public interface AtencionRepository extends JpaRepository<AtencionEntity, Long> {

    @org.springframework.data.jpa.repository.Query("select a.cita.idCita from AtencionEntity a where a.idAtencion=:id")
    java.util.Optional<Long> citaId(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsByCita_IdCita(Long idCita);
}