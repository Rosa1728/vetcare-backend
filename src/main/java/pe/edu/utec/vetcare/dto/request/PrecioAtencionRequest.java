package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Data;
@Data public class PrecioAtencionRequest {
 @NotNull @DecimalMin(value="0",inclusive=false,message="El precio debe ser mayor a 0") private BigDecimal precioAtencion;
}
