package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.RecordatorioVacunaEntity;
import java.time.LocalDate;
import java.util.List;

public interface RecordatorioVacunaRepository extends JpaRepository<RecordatorioVacunaEntity, Long> {
    List<RecordatorioVacunaEntity> findByCorreoEnviadoEnIsNull();
    boolean existsByVacuna_IdVacunaAndFechaDosisAndTipo(Long idVacuna, LocalDate fechaDosis, String tipo);
    List<RecordatorioVacunaEntity> findByVacuna_Mascota_Propietario_Usuario_CorreoOrderByCreadoEnDesc(String correo);
}
