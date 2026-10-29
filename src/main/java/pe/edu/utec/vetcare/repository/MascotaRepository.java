package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.vetcare.entity.MascotaEntity;

import java.util.List;

public interface MascotaRepository
        extends JpaRepository<MascotaEntity, Long> {

    @Query("SELECT m FROM MascotaEntity m WHERE m.propietario.usuario.correo = :correo")
    List<MascotaEntity> findByCorreoPropietario(@Param("correo") String correo);

    @Query("SELECT m FROM MascotaEntity m WHERE EXISTS (SELECT c.idCita FROM CitaEntity c WHERE c.mascota = m AND c.veterinario.usuario.correo = :correo)")
    List<MascotaEntity> findPacientesDeVeterinario(@Param("correo") String correo);

    boolean existsByPropietarioIdPropietario(Long idPropietario);

    List<MascotaEntity>
    findByPropietarioIdPropietario(Long idPropietario);
}