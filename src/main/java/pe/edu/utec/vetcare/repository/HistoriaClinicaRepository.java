package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.HistoriaClinicaEntity;

import java.util.Optional;

public interface HistoriaClinicaRepository
        extends JpaRepository<HistoriaClinicaEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select h from HistoriaClinicaEntity h where h.idHistoria=:id")
    Optional<HistoriaClinicaEntity> bloquear(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<HistoriaClinicaEntity>
    findByMascotaIdMascota(Long idMascota);
}