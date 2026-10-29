package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PropietarioRequest {

    @NotNull(message = "El usuario es obligatorio")
    private Long idUsuario;

    @jakarta.validation.constraints.Pattern(regexp="|9[0-9]{8}", message="Ingresa un celular de 9 dígitos que comience con 9")
    private String nroCelular;

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new pe.edu.utec.vetcare.exception.BadRequestException("Solo se permiten los datos del perfil y su contacto");
    }

    @Size(max = 200)
    private String direccion;
}