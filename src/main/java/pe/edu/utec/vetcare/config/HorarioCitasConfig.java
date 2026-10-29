package pe.edu.utec.vetcare.config;
import org.springframework.context.annotation.*;
import java.time.*;
@Configuration public class HorarioCitasConfig {
 @Bean public Clock relojCitas(){return Clock.system(ZoneId.of("America/Lima"));}
}
