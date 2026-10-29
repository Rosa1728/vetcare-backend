package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class ResolverRegularizacionRequest { @NotNull private Boolean aprobar; }
