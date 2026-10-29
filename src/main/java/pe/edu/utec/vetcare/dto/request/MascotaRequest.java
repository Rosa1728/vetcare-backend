package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MascotaRequest {

    @NotNull(message = "El propietario es obligatorio")
    private Long idPropietario;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @NotBlank(message = "La especie es obligatoria")
    @Size(max = 50)
    private String especie;

    @Size(max = 100)
    private String raza;

    @Size(max = 10)
    private String sexo;

    @PastOrPresent(
            message = "La fecha de nacimiento no puede ser futura"
    )
    private LocalDate fechaNacimiento;
}