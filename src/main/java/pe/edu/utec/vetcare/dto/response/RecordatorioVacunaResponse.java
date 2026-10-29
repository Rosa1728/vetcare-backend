package pe.edu.utec.vetcare.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecordatorioVacunaResponse(Long idRecordatorio, Long idVacuna, Long idMascota,
        String nombreMascota, String nombreVacuna, LocalDate fechaDosis, String tipo,
        String mensaje, LocalDateTime creadoEn, boolean leido) {}
