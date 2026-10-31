package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Edición ordinaria: identidad, credenciales y relaciones no forman parte del contrato. */
@Data
public class ActualizarUsuarioRequest {
    @Size(max=20, message="El celular no puede superar 20 caracteres")
    @Pattern(regexp="|[+]?[0-9][0-9 ()-]{5,19}", message="Ingresa un celular válido de 6 a 20 caracteres.")
    private String nroCelular;
    @Size(max=200, message="La dirección no puede superar 200 caracteres")
    private String direccion;

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rechazarCampo(String nombre,Object valor) {
        throw new pe.edu.utec.vetcare.exception.BadRequestException("La edición de contacto solo permite celular y dirección. Los datos de identificación y acceso están protegidos.");
    }
}
