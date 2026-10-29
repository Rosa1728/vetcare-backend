package pe.edu.utec.vetcare.dto.response;
import java.time.LocalDateTime;
public record RegularizacionResponse(Long idRegularizacion,Long idCita,Long idVeterinario,String paciente,
 String motivo,LocalDateTime fechaSolicitud,String estado,Long idAdministrador,LocalDateTime fechaResolucion,
 LocalDateTime venceEn,LocalDateTime consumidaEn) {}
