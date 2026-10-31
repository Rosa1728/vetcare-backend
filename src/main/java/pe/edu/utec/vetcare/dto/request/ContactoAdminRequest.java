package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
public record ContactoAdminRequest(
 @Pattern(regexp="|9[0-9]{8}", message="Ingresa 9 dígitos, comenzando por 9.") String nroCelular,
 @Size(max=200) String direccion
) {
 @com.fasterxml.jackson.annotation.JsonAnySetter
 public void rechazarCampo(String nombre,Object valor) { throw new pe.edu.utec.vetcare.exception.BadRequestException("Solo se permite editar celular y dirección."); }
}
