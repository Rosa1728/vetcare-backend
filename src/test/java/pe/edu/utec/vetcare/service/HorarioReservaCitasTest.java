package pe.edu.utec.vetcare.service;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class HorarioReservaCitasTest {
 final HorarioReservaCitas reglas=new HorarioReservaCitas("08:00","20:00");
 @ParameterizedTest @ValueSource(strings={"2030-01-11T08:00:00","2030-01-11T19:30:00","2030-01-12T08:00:00","2030-01-12T19:30:00"}) void limitesValidos(String fecha){assertDoesNotThrow(()->reglas.validar(LocalDateTime.parse(fecha)));}
 @ParameterizedTest @ValueSource(strings={"2030-01-11T07:30:00","2030-01-11T20:00:00","2030-01-11T21:00:00","2030-01-11T09:01:00","2030-01-11T09:30:01","2030-01-13T10:00:00"}) void invalidaSinCambiarDatos(String fecha){assertThrows(RuntimeException.class,()->reglas.validar(LocalDateTime.parse(fecha)));}
 @Test void horarioEsConfigurableYValidaConfiguracion(){var otra=new HorarioReservaCitas("09:00","18:00");assertThrows(RuntimeException.class,()->otra.validar(LocalDateTime.parse("2030-01-11T08:00:00")));assertThrows(IllegalArgumentException.class,()->new HorarioReservaCitas("08:15","20:00"));}
}
