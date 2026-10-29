package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.vetcare.entity.CitaEntity;
import pe.edu.utec.vetcare.enums.EstadoCita;

import java.time.LocalDateTime;
import java.util.List;

public interface CitaRepository extends JpaRepository<CitaEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CitaEntity c where c.idCita=:id")
    java.util.Optional<CitaEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByMascota_IdMascota(Long idMascota);

    boolean existsByMascota_IdMascotaAndVeterinario_Usuario_Correo(Long idMascota, String correo);


    @Query("SELECT c FROM CitaEntity c WHERE c.veterinario.usuario.correo = :correo")
    List<CitaEntity> findByCorreoVeterinario(@Param("correo") String correo);

    @Query("SELECT c FROM CitaEntity c WHERE c.mascota.propietario.usuario.correo = :correo")
    List<CitaEntity> findByCorreoPropietario(@Param("correo") String correo);


    @Query("""
        SELECT COUNT(c) > 0
        FROM CitaEntity c
        WHERE c.veterinario.idVeterinario = :idVeterinario
        AND c.fechaCita < :finNuevaCita
        AND c.fechaCita > :inicioComparacion
        AND c.estado <> :estadoExcluido
        AND c.estado <> pe.edu.utec.vetcare.enums.EstadoCita.NO_ASISTIO
    """)
    boolean existsCitaSuperpuesta(
            @Param("idVeterinario") Long idVeterinario,
            @Param("inicioComparacion") LocalDateTime inicioComparacion,
            @Param("finNuevaCita") LocalDateTime finNuevaCita,
            @Param("estadoExcluido") EstadoCita estadoExcluido
    );

    @Query("select c from CitaEntity c where c.veterinario.idVeterinario=:vet and c.fechaCita>:desde and c.fechaCita<:hasta and c.estado in (pe.edu.utec.vetcare.enums.EstadoCita.PENDIENTE, pe.edu.utec.vetcare.enums.EstadoCita.CONFIRMADA)")
    List<CitaEntity> reservasDelDia(@Param("vet") Long vet,@Param("desde") LocalDateTime desde,@Param("hasta") LocalDateTime hasta);

    // Al reprogramar, no comparar la cita con ella misma.
    @Query("""
        SELECT COUNT(c) > 0
        FROM CitaEntity c
        WHERE c.veterinario.idVeterinario = :idVeterinario
        AND c.fechaCita < :finNuevaCita
        AND c.fechaCita > :inicioComparacion
        AND c.estado <> :estadoExcluido
        AND c.estado <> pe.edu.utec.vetcare.enums.EstadoCita.NO_ASISTIO
        AND c.idCita <> :idCitaActual
    """)
    boolean existsOtraCitaSuperpuesta(
            @Param("idVeterinario") Long idVeterinario,
            @Param("inicioComparacion") LocalDateTime inicioComparacion,
            @Param("finNuevaCita") LocalDateTime finNuevaCita,
            @Param("estadoExcluido") EstadoCita estadoExcluido,
            @Param("idCitaActual") Long idCitaActual
    );
}