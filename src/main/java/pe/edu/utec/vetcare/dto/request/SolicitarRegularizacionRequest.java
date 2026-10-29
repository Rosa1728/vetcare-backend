package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class SolicitarRegularizacionRequest {
 @NotBlank(message="Indica el motivo del registro tardío") @Size(max=500,message="El motivo admite hasta 500 caracteres") private String motivo;
}
