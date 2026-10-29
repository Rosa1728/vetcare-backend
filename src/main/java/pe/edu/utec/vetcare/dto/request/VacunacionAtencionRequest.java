package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Datos clínicos; paciente, responsable y atención se resuelven en el servidor. */
public record VacunacionAtencionRequest(
        @NotNull Boolean aplicada,
        @Positive Long idVacunaProgramada,
        @Size(max=120) String nombre,
        LocalDate fechaAplicacion,
        LocalDate proximaDosis,
        @Size(max=500) String observaciones
) {}
