package pe.edu.utec.vetcare.service;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.utec.vetcare.exception.BadRequestException;

/** Horario de reservas; no altera las ventanas de registro clínico existentes. */
@Component
public class HorarioReservaCitas {
 private final LocalTime apertura,cierre;
 public HorarioReservaCitas(@Value("${vetcare.citas.apertura:08:00}") String apertura,
                           @Value("${vetcare.citas.cierre:20:00}") String cierre) {
  this.apertura=LocalTime.parse(apertura);this.cierre=LocalTime.parse(cierre);
  if(this.apertura.getMinute()%30!=0||this.cierre.getMinute()%30!=0||!this.cierre.isAfter(this.apertura))
   throw new IllegalArgumentException("El horario debe estar alineado a 30 minutos y cerrar después de abrir");
 }
 public LocalTime apertura(){return apertura;}
 public LocalTime cierre(){return cierre;}
 public boolean abierto(LocalDate fecha){return fecha.getDayOfWeek()!=DayOfWeek.SUNDAY;}
 public void validar(LocalDateTime fecha){
  if(fecha==null||!abierto(fecha.toLocalDate())||fecha.getSecond()!=0||fecha.getNano()!=0||fecha.getMinute()%30!=0
    ||fecha.toLocalTime().isBefore(apertura)||fecha.isAfter(fecha.toLocalDate().atTime(cierre).minusMinutes(30)))
   throw new BadRequestException("Selecciona un horario de lunes a sábado entre "+apertura+" y "+cierre.minusMinutes(30)+", en intervalos de 30 minutos (America/Lima). Domingos cerrado.");
 }
}
