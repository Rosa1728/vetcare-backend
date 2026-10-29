package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CrearVacunaRequest(
        @NotNull Long idMascota,
        @NotNull Long idVeterinario,
        Long idAtencion,
        @NotBlank @Size(max = 120) String nombre,
        LocalDate fechaAplicacion,
        LocalDate proximaDosis,
        @Size(max = 500) String observaciones
) {}
