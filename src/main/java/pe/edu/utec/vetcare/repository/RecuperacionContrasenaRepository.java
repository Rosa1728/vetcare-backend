package pe.edu.utec.vetcare.repository;
import pe.edu.utec.vetcare.entity.RecuperacionContrasenaEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface RecuperacionContrasenaRepository extends JpaRepository<RecuperacionContrasenaEntity,Long>{
 boolean existsByCorreoAndCreadoEnAfter(String correo,Instant desde);
 List<RecuperacionContrasenaEntity> findTop10ByEstadoOrderByCreadoEnAsc(String estado);
 Optional<RecuperacionContrasenaEntity> findByTokenHash(String hash);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RecuperacionContrasenaEntity r where r.id=:id")
 Optional<RecuperacionContrasenaEntity> bloquear(@Param("id") Long id);
 @Modifying @Query("update RecuperacionContrasenaEntity r set r.usadoEn=:ahora where r.idUsuario=:usuario and r.usadoEn is null")
 void invalidar(@Param("usuario") Long usuario,@Param("ahora") Instant ahora);
}
