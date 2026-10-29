package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrarVeterinarioRequest {
    @jakarta.validation.constraints.Pattern(regexp="Dr[.]|Dra[.]|", message="Selecciona Dr., Dra. o Sin tratamiento")
    private String tratamientoProfesional;

    @NotBlank @Size(max = 100)
    private String nombres;
    @NotBlank @Size(max = 100)
    private String apellidos;
    @NotBlank @Size(max = 20)
    private String tipoDocumento;
    @NotBlank @Size(max = 20)
    private String nroDocumento;
    @NotBlank @Email @Size(max = 150)
    private String correo;
    @NotBlank @pe.edu.utec.vetcare.validation.ContrasenaSegura
    private String contrasena;
    @Size(max = 20)
    private String nroCelular;
    @NotBlank @Size(max = 100)
    private String especialidad;
    @NotBlank @Size(max = 30)
    private String nroColegiatura;
}
