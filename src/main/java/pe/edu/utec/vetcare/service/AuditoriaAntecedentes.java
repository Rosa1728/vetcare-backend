package pe.edu.utec.vetcare.service;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.utec.vetcare.dto.request.AntecedentesRequest;
import pe.edu.utec.vetcare.dto.response.*;
import pe.edu.utec.vetcare.entity.*;
import pe.edu.utec.vetcare.exception.*;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.security.AccesoClinico;
@Service @RequiredArgsConstructor
public class AuditoriaAntecedentes {
 private final CambioAntecedenteRepository cambios;
 private final AtencionRepository atenciones;
 private final Clock clock;
 public static final Map<String,List<String>> SECCIONES=Map.of(
  "alergias",List.of("estadoAlergias","alergiasSustancias","alergiasReacciones","alergiasObservaciones"),
  "medicos",List.of("enfermedadesRelevantes"),"cirugias",List.of("cirugiasPrevias"),"cuidados",List.of("notasMedicas","alertasMedicas"));
 public java.time.LocalDateTime ahora(){return java.time.LocalDateTime.ofInstant(clock.instant(),java.time.ZoneId.of("America/Lima"));}
 public Map<String,String> valores(HistoriaClinicaEntity h){var v=new LinkedHashMap<String,String>();
  v.put("estadoAlergias",h.getEstadoAlergias().name());v.put("alergiasSustancias",h.getAlergiasSustancias());v.put("alergiasReacciones",h.getAlergiasReacciones());v.put("alergiasObservaciones",h.getAlergiasObservaciones());v.put("enfermedadesRelevantes",h.getEnfermedadesRelevantes());v.put("cirugiasPrevias",h.getCirugiasPrevias());v.put("notasMedicas",h.getNotasMedicas());v.put("alertasMedicas",h.getAlertasMedicas());return v;
 }
 public Map<String,String> valores(AntecedentesRequest r){var h=new HistoriaClinicaEntity();h.setEstadoAlergias(r.estadoAlergias());h.setAlergiasSustancias(r.alergiasSustancias());h.setAlergiasReacciones(r.alergiasReacciones());h.setAlergiasObservaciones(r.alergiasObservaciones());h.setEnfermedadesRelevantes(r.enfermedadesRelevantes());h.setCirugiasPrevias(r.cirugiasPrevias());h.setNotasMedicas(r.notasMedicas());h.setAlertasMedicas(r.alertasMedicas());return valores(h);}
 private String comparable(String value){return value!=null&&value.isBlank()?null:value;}
 public boolean registrar(HistoriaClinicaEntity h,AntecedentesRequest r,VeterinarioEntity vet){
  if(r.version()==null)throw new BadRequestException("Vuelve a cargar el expediente antes de editar.");
  if(r.version()!=h.getVersionAntecedentes())throw new EstadoCitaInvalidoException("El expediente cambió mientras editabas. Conserva tu texto y vuelve a cargarlo antes de guardar.");
  var anterior=valores(h);var nuevo=valores(r);var keys=SECCIONES.keySet().stream().filter(s->SECCIONES.get(s).stream().anyMatch(k->!Objects.equals(comparable(anterior.get(k)),comparable(nuevo.get(k))))).sorted().toList();
  if(keys.isEmpty())return false;
  boolean correccion=keys.stream().flatMap(s->SECCIONES.get(s).stream()).anyMatch(k->!Objects.equals(comparable(anterior.get(k)),comparable(nuevo.get(k)))&&anterior.get(k)!=null&&!anterior.get(k).isBlank()&&!(k.equals("estadoAlergias")&&anterior.get(k).equals("NO_EVALUADAS")));
  if(correccion&&(r.motivo()==null||r.motivo().isBlank()))throw new BadRequestException("Indica el motivo al corregir o retirar información existente.");
  AtencionEntity atencion=null;
  if(r.idAtencion()!=null){atencion=atenciones.findById(r.idAtencion()).orElseThrow(()->new ResourceNotFoundException("Atención no encontrada"));
   if(!atencion.getHistoriaClinica().getIdHistoria().equals(h.getIdHistoria())||!atencion.getVeterinarioAtencion().getIdVeterinario().equals(vet.getIdVeterinario()))throw new org.springframework.security.access.AccessDeniedException("La atención no corresponde al expediente y al veterinario responsable");}
  var fecha=clock.instant();var usuario=vet.getUsuario();
  for(String key:keys){var old=new LinkedHashMap<String,String>();var fresh=new LinkedHashMap<String,String>();for(String field:SECCIONES.get(key)){old.put(field,anterior.get(field));fresh.put(field,nuevo.get(field));}
   cambios.save(new CambioAntecedenteEntity(null,h,key,old,fresh,vet,usuario.getNombres()+" "+usuario.getApellidos(),fecha,r.motivo()==null?null:r.motivo().trim(),atencion,h.getVersionAntecedentes()+1));}
  return true;
 }
 public CambioAntecedenteResponse.Pagina consultar(Long historia,String seccion,int pagina,int size){
  if(pagina<0||size<1||size>100||(seccion!=null&&!SECCIONES.containsKey(seccion)))throw new BadRequestException("Filtro o paginación de antecedentes inválidos");
  var result=cambios.consultar(historia,seccion,org.springframework.data.domain.PageRequest.of(pagina,size));
  var items=result.getContent().stream().map(c->new CambioAntecedenteResponse(c.getId(),historia,c.getHistoria().getMascota().getIdMascota(),c.getSeccion(),c.getAnterior(),c.getNuevo(),c.getVeterinario().getIdVeterinario(),c.getNombreVeterinario(),c.getFecha(),AccesoClinico.es("PROPIETARIO")?null:c.getMotivo(),c.getAtencion()==null?null:c.getAtencion().getIdAtencion(),c.getRevision())).toList();
  return new CambioAntecedenteResponse.Pagina(items,result.getTotalElements(),pagina,result.getTotalPages());
 }
}
