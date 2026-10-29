package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
public record ContactoAdminRequest(
 @Pattern(regexp="9[0-9]{8}", message="Ingresa 9 dígitos, comenzando por 9.") String nroCelular,
 @Size(max=200) String direccion
) {}
