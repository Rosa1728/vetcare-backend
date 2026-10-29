package pe.edu.utec.vetcare.dto.response;
import java.time.Instant;
import java.util.*;
public record CambioAntecedenteResponse(Long id,Long idHistoria,Long idMascota,String seccion,
 Map<String,String> anterior,Map<String,String> nuevo,Long idVeterinario,String veterinarioNombre,
 Instant fecha,String motivo,Long idAtencion,long revision) {
 public record Pagina(List<CambioAntecedenteResponse> items,long total,int pagina,int totalPaginas){}
}
