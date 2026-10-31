package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.RecordatorioVacunaEntity;
import java.time.LocalDate;
import java.util.List;

public interface RecordatorioVacunaRepository extends JpaRepository<RecordatorioVacunaEntity, Long> {
    @org.springframework.data.jpa.repository.Query("select r from RecordatorioVacunaEntity r where r.correoEnviadoEn is null and r.fechaDosis=r.vacuna.proximaDosis and not exists (select n.idVacuna from VacunaEntity n where n.idVacunaOrigen=r.vacuna.idVacuna)")
    List<RecordatorioVacunaEntity> findByCorreoEnviadoEnIsNull();

    // La clave ya existente resuelve también dos ejecuciones concurrentes sin abortar la transacción clínica.
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="""
        INSERT INTO recordatorio_vacuna (id_vacuna,fecha_dosis,tipo,creado_en)
        SELECT v.id_vacuna,:fecha,:tipo,:creado FROM vacuna v
        WHERE v.id_vacuna=:id AND v.proxima_dosis=:fecha
          AND NOT EXISTS (SELECT 1 FROM vacuna n WHERE n.id_vacuna_origen=v.id_vacuna)
        ON CONFLICT ON CONSTRAINT uq_recordatorio_dosis_tipo DO NOTHING
        """,nativeQuery=true)
    int crearSiFalta(@org.springframework.data.repository.query.Param("id") Long id,
        @org.springframework.data.repository.query.Param("fecha") LocalDate fecha,
        @org.springframework.data.repository.query.Param("tipo") String tipo,
        @org.springframework.data.repository.query.Param("creado") java.time.LocalDateTime creado);
    boolean existsByVacuna_IdVacunaAndFechaDosisAndTipo(Long idVacuna, LocalDate fechaDosis, String tipo);
    List<RecordatorioVacunaEntity> findByVacuna_Mascota_Propietario_Usuario_CorreoOrderByCreadoEnDesc(String correo);
}
