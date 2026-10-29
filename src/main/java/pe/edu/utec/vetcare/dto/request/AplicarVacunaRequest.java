package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AplicarVacunaRequest(
        @NotNull Long idAtencion,
        @NotNull LocalDate fechaAplicacion,
        LocalDate proximaDosis,
        @Size(max = 500) String observaciones
) {}
