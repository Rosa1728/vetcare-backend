package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.RolEntity;

import java.util.Optional;

public interface RolRepository
        extends JpaRepository<RolEntity, Long> {

    Optional<RolEntity> findByNombre(String nombre);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RolEntity r where r.nombre='ADMIN'")
    Optional<RolEntity> bloquearAdministracion();
}