package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroPropietarioRequest(
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp="DNI|CE|PASAPORTE") String tipoDocumento,
        @NotBlank @Size(max = 20) String nroDocumento,
        @NotBlank @Email @Size(max = 150) String correo,
        @NotBlank @pe.edu.utec.vetcare.validation.ContrasenaSegura String contrasena,
        @Size(max = 20) @jakarta.validation.constraints.Pattern(regexp="|[+]?[0-9][0-9 ()-]{5,19}") String nroCelular,
        @Size(max = 200) String direccion
) {}
