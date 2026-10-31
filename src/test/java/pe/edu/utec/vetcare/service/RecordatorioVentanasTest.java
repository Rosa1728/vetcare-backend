package pe.edu.utec.vetcare.service;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.utec.vetcare.entity.*;
import pe.edu.utec.vetcare.repository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RecordatorioVentanasTest {
 private final LocalDate today=LocalDate.of(2026,11,3);
 @ParameterizedTest @CsvSource({"8,NINGUNO","7,DIA_7","6,DIA_7","3,DIA_7","2,DIA_7","1,DIA_1","0,HOY","-1,VENCIDA"})
 void capturaVentanaSinEsperarDiaExacto(int days,String tipo){assertEquals(tipo.equals("NINGUNO")?null:tipo,RecordatorioVacunaService.tipoPendiente(today,today.plusDays(days)));}
 @Test void recuperaAutomaticamenteYUsaRelojLimaYCreacionIdempotente(){
  var reminders=mock(RecordatorioVacunaRepository.class);var doses=mock(VacunaRepository.class);var v=new VacunaEntity();v.setIdVacuna(1L);v.setProximaDosis(today.plusDays(3));when(doses.findByProximaDosisIsNotNull()).thenReturn(List.of(v));
  when(reminders.crearSiFalta(eq(1L),eq(v.getProximaDosis()),eq("DIA_7"),any())).thenReturn(1,0);
  var service=new RecordatorioVacunaService(reminders,doses,Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"),ZoneOffset.UTC));
  service.ejecutarAutomaticamente();assertEquals(0,service.sincronizar(v));verify(reminders,times(2)).crearSiFalta(1L,v.getProximaDosis(),"DIA_7",today.atTime(21,0));
 }
}
