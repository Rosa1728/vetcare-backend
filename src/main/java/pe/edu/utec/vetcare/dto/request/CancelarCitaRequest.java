package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class CancelarCitaRequest {
 @NotBlank @Pattern(regexp="Inconveniente con el horario\\.|Motivos personales\\.|Ya no necesito la consulta\\.|Otro motivo\\.",message="Selecciona un motivo de cancelación válido") private String motivo;
 @Size(max=500,message="La descripción admite hasta 500 caracteres") private String descripcion;
}
