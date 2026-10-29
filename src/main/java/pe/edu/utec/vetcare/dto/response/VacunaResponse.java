package pe.edu.utec.vetcare.dto.response;

import java.time.LocalDate;

public record VacunaResponse(
        Long idVacuna, Long idMascota, Long idVeterinario, Long idAtencion,
        String nombre, LocalDate fechaAplicacion, LocalDate proximaDosis,
        String observaciones, String estado, String estadoAplicacion, String estadoProximaDosis, LocalDate fechaProgramada, Long idVacunaOrigen
) {}
