package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActualizarDireccionPropietarioRequest {
    @NotNull(message = "La dirección es obligatoria")
    @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
    private String direccion;
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rechazarCampo(String nombre,Object valor) { throw new pe.edu.utec.vetcare.exception.BadRequestException("Solo se permite actualizar la dirección."); }
}
