package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioRequest {

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 100)
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100)
    private String apellidos;

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Size(max=20)
    private String tipoDocumento;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max=20)
    private String nroDocumento;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    @Size(max=150)
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    @pe.edu.utec.vetcare.validation.ContrasenaSegura
    private String contrasena;

    @Size(max=20)
    private String nroCelular;

    @NotNull(message = "El rol es obligatorio")
    private Long idRol;

    private Boolean confirmarAdmin;
}