package pe.edu.utec.vetcare.dto.response;
import java.time.*;
import java.util.List;
public record DisponibilidadCitaResponse(Long idVeterinario,LocalDate fecha,String zonaHoraria,
 LocalDateTime horaServidor,LocalTime apertura,LocalTime cierre,int duracionMinutos,List<Horario> horarios){
 public record Horario(LocalDateTime inicio,LocalDateTime fin,boolean disponible){}
}
