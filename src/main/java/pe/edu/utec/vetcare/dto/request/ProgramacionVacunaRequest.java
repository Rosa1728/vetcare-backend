package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record ProgramacionVacunaRequest(@NotBlank @Size(max=120) String nombre,
        @NotNull LocalDate proximaDosis, @Size(max=500) String observaciones) {}
