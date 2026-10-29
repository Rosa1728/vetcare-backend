package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CambiarContrasenaRequest(
 @NotBlank(message="Ingresa tu contraseña actual.") @Size(max=200,message="Revisa tu contraseña actual.") String contrasenaActual,
 @NotBlank(message="Ingresa la nueva contraseña.") @Size(max=200,message="Elige una contraseña más corta.") String nuevaContrasena,
 @NotBlank(message="Confirma la nueva contraseña.") @Size(max=200,message="Revisa la confirmación.") String confirmarContrasena
) {}
