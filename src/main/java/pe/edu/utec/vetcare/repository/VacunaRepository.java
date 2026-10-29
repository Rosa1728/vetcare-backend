package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.VacunaEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacunaRepository extends JpaRepository<VacunaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VacunaEntity v where v.idVacuna=:id")
    Optional<VacunaEntity> findLockedById(@Param("id") Long id);
    boolean existsByAtencion_IdAtencionAndNombreIgnoreCase(Long idAtencion, String nombre);
    boolean existsByAtencion_IdAtencionAndNombreIgnoreCaseAndIdVacunaNot(Long idAtencion, String nombre, Long idVacuna);
    boolean existsByIdVacunaOrigen(Long id);
    @Query("select v from VacunaEntity v where v.proximaDosis is not null and not exists (select n.idVacuna from VacunaEntity n where n.idVacunaOrigen=v.idVacuna)")
    List<VacunaEntity> findByProximaDosisIsNotNull();
    List<VacunaEntity> findByMascota_IdMascotaOrderByProximaDosisAsc(Long idMascota);
    List<VacunaEntity> findByProximaDosisBetweenOrderByProximaDosisAsc(LocalDate desde, LocalDate hasta);
    List<VacunaEntity> findByProximaDosisBeforeOrderByProximaDosisAsc(LocalDate fecha);
}
