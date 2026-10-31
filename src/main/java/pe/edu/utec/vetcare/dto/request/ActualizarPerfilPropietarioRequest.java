package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActualizarPerfilPropietarioRequest {
    @Size(max=20,message="El celular no puede superar 20 caracteres") @Pattern(regexp="|\\+?[0-9][0-9 ()-]{5,19}",message="Indica un celular válido de 6 a 20 caracteres")
    private String nroCelular;
    @Size(max=200,message="La dirección no puede superar 200 caracteres")
    private String direccion;
}
