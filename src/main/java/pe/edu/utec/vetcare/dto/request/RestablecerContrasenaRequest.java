package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
public record RestablecerContrasenaRequest(
 @NotBlank @Size(min=43,max=43) String token,
 @NotBlank @pe.edu.utec.vetcare.validation.ContrasenaSegura String contrasena
){}
