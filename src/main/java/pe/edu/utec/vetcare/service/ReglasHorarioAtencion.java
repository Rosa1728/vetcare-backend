package pe.edu.utec.vetcare.service;
import org.springframework.stereotype.Component;
import java.time.*;
import pe.edu.utec.vetcare.entity.CitaEntity;
import pe.edu.utec.vetcare.enums.EstadoCita;
@Component public class ReglasHorarioAtencion {
 private final Clock clock;
 public ReglasHorarioAtencion(Clock clock){this.clock=clock;}
 public LocalDateTime ahora(){return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("America/Lima"));}
 public boolean enHorario(CitaEntity c){var n=ahora();return c.getEstado()==EstadoCita.CONFIRMADA && !n.isBefore(c.getFechaCita()) && n.isBefore(c.getFechaCita().plusMinutes(30));}
 public boolean enTolerancia(CitaEntity c,Long usuario){var n=ahora();return c.getEstado()==EstadoCita.CONFIRMADA && c.getInicioRegistro()!=null && usuario.equals(c.getIdUsuarioInicio()) && !c.getInicioRegistro().isBefore(c.getFechaCita()) && c.getInicioRegistro().isBefore(c.getFechaCita().plusMinutes(30)) && !n.isBefore(c.getFechaCita().plusMinutes(30)) && !n.isAfter(c.getFechaCita().plusMinutes(45));}
 public boolean vencida(CitaEntity c){return !ahora().isBefore(c.getFechaCita().plusMinutes(30));}
}
