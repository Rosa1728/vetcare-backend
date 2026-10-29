package pe.edu.utec.vetcare.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.vetcare.dto.request.CancelarCitaRequest;
import pe.edu.utec.vetcare.dto.response.*;
import pe.edu.utec.vetcare.entity.*;
import pe.edu.utec.vetcare.enums.EstadoCita;
import pe.edu.utec.vetcare.exception.*;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.security.AccesoClinico;

@Service @Transactional
public class SeguimientoCitaService {
 private final CitaRepository citas;
 private final VeterinarioRepository veterinarios;
 private final RegularizacionAtencionRepository solicitudes;
 private final ReglasHorarioAtencion horario;
 private final long vigenciaMinutos;
 public SeguimientoCitaService(CitaRepository citas,VeterinarioRepository veterinarios,RegularizacionAtencionRepository solicitudes,ReglasHorarioAtencion horario,@Value("${vetcare.citas.regularizacion-vigencia-minutos:1440}") long vigenciaMinutos){
  this.citas=citas;this.veterinarios=veterinarios;this.solicitudes=solicitudes;this.horario=horario;
  if(vigenciaMinutos<1 || vigenciaMinutos>10080)throw new IllegalArgumentException("La vigencia debe estar entre 1 y 10080 minutos");this.vigenciaMinutos=vigenciaMinutos;
 }
 public Long usuarioActual(){var a=SecurityContextHolder.getContext().getAuthentication();if(a instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaim("idUsuario") instanceof Number id)return id.longValue();throw new AccessDeniedException("Sesión sin identidad válida");}
 public CitaEntity bloquear(Long id){return citas.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Cita no encontrada"));}
 public void serializarAgenda(Long id){veterinarios.bloquearAgenda(id).orElseThrow(()->new ResourceNotFoundException("Veterinario no encontrado"));}
 public void validarFuturo(LocalDateTime fecha){if(fecha==null || !fecha.isAfter(horario.ahora()))throw new BadRequestException("La fecha y hora deben ser futuras (America/Lima)");}
 private void profesional(CitaEntity c){if(!AccesoClinico.es("ADMIN") && !(AccesoClinico.es("VETERINARIO") && AccesoClinico.esVeterinario(c)))throw new AccessDeniedException("Cita ajena");}
 private void confirmada(CitaEntity c){if(c.getEstado()!=EstadoCita.CONFIRMADA)throw new EstadoCitaInvalidoException("La cita debe estar CONFIRMADA");}
 public CitaEntity iniciar(Long id){var c=bloquear(id);profesional(c);confirmada(c);
  if(horario.vencida(c)&&aprobada(c)!=null)return c;
  if(!horario.enHorario(c))throw new EstadoCitaInvalidoException("Solo puedes iniciar la atención durante los 30 minutos de la cita o con regularización aprobada");
  c.setInicioRegistro(horario.ahora());c.setIdUsuarioInicio(usuarioActual());return c;
 }
 public CitaEntity noAsistio(Long id){var c=bloquear(id);profesional(c);
  if(!List.of(EstadoCita.PENDIENTE,EstadoCita.CONFIRMADA).contains(c.getEstado()) || !horario.vencida(c))throw new EstadoCitaInvalidoException("Solo se puede marcar inasistencia después de concluir una cita activa");
  invalidarRegularizacion(c);c.setEstado(EstadoCita.NO_ASISTIO);return c;
 }
 public void registrarCancelacion(CitaEntity c,CancelarCitaRequest request){
  if(horario.vencida(c))throw new EstadoCitaInvalidoException("No se puede cancelar una cita vencida. Registra el resultado clínico o la inasistencia.");
  if("Otro motivo.".equals(request.getMotivo()) && (request.getDescripcion()==null || request.getDescripcion().isBlank()))throw new BadRequestException("Describe el otro motivo de cancelación");
  invalidarRegularizacion(c);c.setMotivoCancelacion(request.getMotivo());c.setDetalleCancelacion(request.getDescripcion());c.setIdUsuarioCancelacion(usuarioActual());
  c.setRolCancelacion(AccesoClinico.es("ADMIN")?"ADMIN":AccesoClinico.es("VETERINARIO")?"VETERINARIO":"PROPIETARIO");c.setFechaCancelacion(horario.ahora());
 }
 private RegularizacionAtencionEntity ultima(CitaEntity c){return solicitudes.findFirstByCita_IdCitaOrderByIdRegularizacionDesc(c.getIdCita()).orElse(null);}
 private boolean vigente(RegularizacionAtencionEntity r){return r!=null && "APROBADA".equals(r.getEstado()) && r.getVenceEn()!=null && horario.ahora().isBefore(r.getVenceEn());}
 private RegularizacionAtencionEntity aprobada(CitaEntity c){var r=ultima(c);return vigente(r)?r:null;}
 public RegularizacionAtencionEntity autorizarRegistro(CitaEntity c){profesional(c);confirmada(c);
  if(horario.enHorario(c)||horario.enTolerancia(c,usuarioActual()))return null;
  var permiso=aprobada(c);if(permiso!=null && horario.vencida(c))return permiso;
  throw new EstadoCitaInvalidoException(horario.ahora().isBefore(c.getFechaCita())?"Podrás registrar la atención cuando comience el horario de esta cita.":"El plazo de registro terminó. Solicita regularización a ADMIN.");
 }
 public void consumir(RegularizacionAtencionEntity r){if(r!=null){r.setEstado("CONSUMIDA");r.setConsumidaEn(horario.ahora());}}
 public RegularizacionResponse solicitar(Long id,String motivo){var c=bloquear(id);
  if(!AccesoClinico.es("VETERINARIO") || !AccesoClinico.esVeterinario(c))throw new AccessDeniedException("Solo el veterinario asignado puede solicitar regularización");
  confirmada(c);if(!horario.vencida(c))throw new EstadoCitaInvalidoException("La cita todavía no ha vencido");
  var anterior=ultima(c);if(anterior!=null && ("PENDIENTE".equals(anterior.getEstado()) || vigente(anterior)))throw new EstadoCitaInvalidoException("Ya existe una solicitud pendiente o autorización vigente para esta cita");
  if(anterior!=null && "APROBADA".equals(anterior.getEstado())){anterior.setEstado("EXPIRADA");solicitudes.flush();}
  var r=new RegularizacionAtencionEntity();r.setCita(c);r.setIdVeterinario(c.getVeterinario().getIdVeterinario());r.setMotivo(motivo.trim());r.setFechaSolicitud(horario.ahora());r.setEstado("PENDIENTE");return respuesta(solicitudes.save(r));
 }
 public RegularizacionResponse resolver(Long id,boolean aprobar){AccesoClinico.soloAdmin();
  var citaId=solicitudes.citaId(id).orElseThrow(()->new ResourceNotFoundException("Solicitud no encontrada"));var c=bloquear(citaId);
  // Todas las transiciones y el consumo toman primero el mismo bloqueo de cita.
  var r=solicitudes.findById(id).orElseThrow();
  if(!"PENDIENTE".equals(r.getEstado()))throw new EstadoCitaInvalidoException("La solicitud ya fue resuelta");
  confirmada(c);if(!horario.vencida(c))throw new EstadoCitaInvalidoException("La cita no está vencida");
  r.setEstado(aprobar?"APROBADA":"RECHAZADA");r.setIdAdministrador(usuarioActual());r.setFechaResolucion(horario.ahora());if(aprobar)r.setVenceEn(horario.ahora().plusMinutes(vigenciaMinutos));return respuesta(r);
 }
 @Transactional(readOnly=true) public List<RegularizacionResponse> listar(){
  if(AccesoClinico.es("ADMIN"))return solicitudes.findAllByOrderByFechaSolicitudDescIdRegularizacionDesc().stream().map(this::respuesta).toList();
  if(!AccesoClinico.es("VETERINARIO"))throw new AccessDeniedException("Operación clínica");
  var v=veterinarios.findByUsuario_Correo(AccesoClinico.correo()).orElseThrow(()->new ResourceNotFoundException("Veterinario no encontrado"));return solicitudes.findByIdVeterinarioOrderByFechaSolicitudDescIdRegularizacionDesc(v.getIdVeterinario()).stream().map(this::respuesta).toList();
 }
 private RegularizacionResponse respuesta(RegularizacionAtencionEntity r){return new RegularizacionResponse(r.getIdRegularizacion(),r.getCita().getIdCita(),r.getIdVeterinario(),r.getCita().getMascota().getNombre(),r.getMotivo(),r.getFechaSolicitud(),"APROBADA".equals(r.getEstado())&&!vigente(r)?"EXPIRADA":r.getEstado(),r.getIdAdministrador(),r.getFechaResolucion(),r.getVenceEn(),r.getConsumidaEn());}
 public void validarGestionPropietario(CitaEntity c) {
  if (!c.getFechaCita().isAfter(horario.ahora()))
   throw new EstadoCitaInvalidoException("No puedes cancelar ni reprogramar una cita que ya comenzó. La clínica debe actualizar su resultado.");
 }
 public void invalidarRegularizacion(CitaEntity c){var r=ultima(c);if(r!=null && List.of("PENDIENTE","APROBADA").contains(r.getEstado())){r.setEstado("EXPIRADA");r.setVenceEn(horario.ahora());}}
 public void comprobarEliminacion(CitaEntity c){if(ultima(c)!=null)throw new EstadoCitaInvalidoException("No se puede eliminar una cita con solicitudes de regularización auditadas");}
 public CitaResponse completar(CitaEntity c,CitaResponse dto){
  boolean activa=List.of(EstadoCita.PENDIENTE,EstadoCita.CONFIRMADA).contains(c.getEstado());
  boolean propia=AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(c);
  boolean futura=c.getFechaCita().isAfter(horario.ahora());
  dto.setPuedeCancelar(activa && !horario.vencida(c) && (AccesoClinico.es("ADMIN") || (AccesoClinico.es("VETERINARIO") && AccesoClinico.esVeterinario(c)) || (propia && futura)));
  dto.setPuedeReprogramar(activa && (AccesoClinico.es("ADMIN") || (propia && futura && c.getEstado()==EstadoCita.PENDIENTE)));
  dto.setHoraServidor(horario.ahora());dto.setFinRegistroAtencion(c.getFechaCita().plusMinutes(45));
  if(AccesoClinico.es("ADMIN") || (AccesoClinico.es("VETERINARIO")&&AccesoClinico.esVeterinario(c))){
   var r=ultima(c);if(r!=null)dto.setRegularizacion(respuesta(r));boolean excepcional=c.getEstado()==EstadoCita.CONFIRMADA && horario.vencida(c) && vigente(r);
   dto.setPuedeIniciarAtencion(horario.enHorario(c)||excepcional);dto.setPuedeGuardarAtencion(horario.enHorario(c)||horario.enTolerancia(c,usuarioActual())||excepcional);
  }return dto;
 }
}
