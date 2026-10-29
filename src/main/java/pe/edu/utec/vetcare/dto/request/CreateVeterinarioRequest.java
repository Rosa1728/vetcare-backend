package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateVeterinarioRequest {
    @jakarta.validation.constraints.Pattern(regexp="Dr[.]|Dra[.]|", message="Selecciona Dr., Dra. o Sin tratamiento")
    private String tratamientoProfesional;



    @NotNull(message = "El usuario es obligatorio")
    private Long idUsuario;

    @NotBlank(message = "La especialidad es obligatoria")
    @Size(max = 100)
    private String especialidad;

    @NotBlank(message = "El número de colegiatura es obligatorio")
    @Size(max = 30)
    private String nroColegiatura;
}