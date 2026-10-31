package pe.edu.utec.vetcare.integration;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(CitasClinicasHttpIT.TimeConfig.class)
@TestPropertySource(properties="vetcare.citas.regularizacion-vigencia-minutos=60")
class CitasClinicasHttpIT extends PostgresIntegrationSupport {
 static class MutableClock extends Clock {
  volatile Instant instant=Instant.parse("2030-01-10T15:10:00Z");
  public ZoneId getZone(){return ZoneId.of("America/Lima");}
  public Clock withZone(ZoneId zone){return Clock.fixed(instant,zone);}
  public Instant instant(){return instant;}
  void at(String lima){instant=LocalDateTime.parse(lima).atZone(getZone()).toInstant();}
 }
 @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock testClock(){return new MutableClock();} }
 @Autowired MutableClock clock; @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
 String password,suffix,admin,vet,owner,otherVet;long petId,vetId,history;
 @BeforeEach void fixture() throws Exception {
  assertEquals("vetcare_p0_integration",jdbc.queryForObject("select current_database()",String.class));
  clock.at("2030-01-10T10:10:00");password=UUID.randomUUID().toString();suffix=UUID.randomUUID().toString().substring(0,8);
  long a=user("ADMIN","admin"),v=user("VETERINARIO","vet"),o=user("PROPIETARIO","owner"),w=user("VETERINARIO","other");
  long p=jdbc.queryForObject("insert into propietario(id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,o);
  vetId=jdbc.queryForObject("insert into veterinario(id_usuario,nro_colegiatura) values (?,?) returning id_veterinario",Long.class,v,suffix);
  jdbc.update("insert into veterinario(id_usuario,nro_colegiatura) values (?,?)",w,"W-"+suffix);
  petId=jdbc.queryForObject("insert into mascota(id_propietario,nombre,especie) values (?,'Paciente sintético','Canino') returning id_mascota",Long.class,p);
  history=jdbc.queryForObject("insert into historia_clinica(id_mascota,fecha_apertura) values (?,current_timestamp) returning id_historia",Long.class,petId);
  admin=login("admin");vet=login("vet");owner=login("owner");otherVet=login("other");
 }
 @Test void disponibilidadYConsultaNumericaSeRegistranSeparadasSinAlterarCita() throws Exception {
  long cita=cita("2030-01-11T10:00:00","PENDIENTE");var original=jdbc.queryForMap("select * from cita where id_cita=?",cita);
  mvc.perform(get("/api/citas/disponibilidad").queryParam("idVeterinario",Long.toString(vetId)).queryParam("fecha","2026-10-28").header("Authorization",owner))
   .andExpect(handler().handlerType(pe.edu.utec.vetcare.controller.CitaController.class)).andExpect(handler().methodName("disponibilidad"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.idVeterinario").value(vetId)).andExpect(jsonPath("$.fecha").value("2026-10-28"));
  mvc.perform(get("/api/citas/{id}",cita).header("Authorization",owner))
   .andExpect(handler().methodName("getById")).andExpect(status().isOk()).andExpect(jsonPath("$.idCita").value(cita));
  assertEquals(original,jdbc.queryForMap("select * from cita where id_cita=?",cita));
 }
 @Test void contratoHttpDisponibilidadUsaRutaLiteralYParametrosIsoDelFrontend() throws Exception {
  mvc.perform(get("/api/citas/disponibilidad").queryParam("idVeterinario",Long.toString(vetId)).queryParam("fecha","2026-10-27").header("Authorization",owner))
   .andExpect(handler().methodName("disponibilidad")).andExpect(status().isOk()).andExpect(jsonPath("$.fecha").value("2026-10-27")).andExpect(jsonPath("$.horarios.length()").value(24));
  mvc.perform(get("/api/citas/disponibilidad").queryParam("idVeterinario",Long.toString(vetId)).queryParam("fecha","27/10/2026").header("Authorization",owner)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/citas/disponibilidad").queryParam("fecha","2030-01-11").header("Authorization",owner)).andExpect(status().isBadRequest());
 }
 @Test void reservaRealBloqueaElSlotYCancelacionLoLibera() throws Exception {
  long id=json.readTree(reservar("2030-01-11T10:00:00",vetId,owner).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("idCita").asLong();
  mvc.perform(get("/api/citas/disponibilidad").queryParam("idVeterinario",Long.toString(vetId)).queryParam("fecha","2030-01-11").header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.horarios[4].disponible").value(false)).andExpect(jsonPath("$.horarios[5].disponible").value(true));
  cancelar(id,owner).andExpect(status().isOk());
  mvc.perform(get("/api/citas/disponibilidad").queryParam("idVeterinario",Long.toString(vetId)).queryParam("fecha","2030-01-11").header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.horarios[4].disponible").value(true));
 }
 @Test void disponibilidadPrivadaPorProfesionalRespetaHorarioYNoExponePacientes() throws Exception {
  cita("2030-01-11T10:00:00","PENDIENTE");cita("2030-01-11T11:00:00","CONFIRMADA");
  cita("2030-01-11T12:00:00","CANCELADA");cita("2030-01-11T13:00:00","NO_ASISTIO");
  var data=json.readTree(mvc.perform(get("/api/citas/disponibilidad").param("idVeterinario",""+vetId).param("fecha","2030-01-11").header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.zonaHoraria").value("America/Lima")).andReturn().getResponse().getContentAsByteArray());
  var slots=data.get("horarios");assertEquals(24,slots.size());assertEquals("2030-01-11T08:00:00",slots.get(0).get("inicio").asText());assertEquals("2030-01-11T19:30:00",slots.get(23).get("inicio").asText());assertEquals("2030-01-11T20:00:00",slots.get(23).get("fin").asText());
  assertFalse(slots.get(4).get("disponible").asBoolean());assertTrue(slots.get(5).get("disponible").asBoolean());assertFalse(slots.get(6).get("disponible").asBoolean());assertTrue(slots.get(8).get("disponible").asBoolean());assertTrue(slots.get(10).get("disponible").asBoolean());
  assertFalse(data.toString().contains("idMascota"));assertFalse(data.toString().contains("motivoCita"));
  mvc.perform(get("/api/citas/disponibilidad").param("idVeterinario",""+vetId).param("fecha","2030-01-11")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/citas/disponibilidad").param("idVeterinario",""+vetId).param("fecha","2030-01-11").header("Authorization",otherVet)).andExpect(status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"2030-01-11T07:30:00","2030-01-11T20:00:00","2030-01-11T21:00:00","2030-01-13T10:00:00","2030-01-11T09:15:00","2030-01-11T09:30:01","2030-01-10T10:00:00"})
 void reservasFueraDeHorarioRechazadasSinGuardar(String fecha) throws Exception {
  long before=jdbc.queryForObject("select count(*) from cita where id_mascota=?",Long.class,petId);
  reservar(fecha,vetId,owner).andExpect(status().isBadRequest());assertEquals(before,jdbc.queryForObject("select count(*) from cita where id_mascota=?",Long.class,petId));
 }
 @Test void sabadoTieneMismoHorarioDomingoCerradoYPasadosDeshabilitados() throws Exception {
  for(String day:List.of("2030-01-11","2030-01-12"))mvc.perform(get("/api/citas/disponibilidad").param("idVeterinario",""+vetId).param("fecha",day).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.horarios.length()").value(24));
  mvc.perform(get("/api/citas/disponibilidad").param("idVeterinario",""+vetId).param("fecha","2030-01-13").header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.horarios.length()").value(0));
  var slots=json.readTree(mvc.perform(get("/api/citas/disponibilidad").param("idVeterinario",""+vetId).param("fecha","2030-01-10").header("Authorization",owner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("horarios");assertFalse(slots.get(4).get("disponible").asBoolean());assertTrue(slots.get(5).get("disponible").asBoolean());
 }
 @Test void limitesContiguosYAgendasDiferentesSeGuardanYSeVenPorRoles() throws Exception {
  reservar("2030-01-11T08:00:00",vetId,owner).andExpect(status().isOk());
  var saved=json.readTree(reservar("2030-01-11T10:00:00",vetId,owner).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE")).andReturn().getResponse().getContentAsByteArray());long id=saved.get("idCita").asLong();
  reservar("2030-01-11T10:30:00",vetId,owner).andExpect(status().isOk());reservar("2030-01-12T19:30:00",vetId,owner).andExpect(status().isOk());
  long other=jdbc.queryForObject("select id_veterinario from veterinario where nro_colegiatura=?",Long.class,"W-"+suffix);reservar("2030-01-11T10:00:00",other,owner).andExpect(status().isOk());
  for(String token:List.of(owner,vet))mvc.perform(get("/api/citas/{id}",id).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
  mvc.perform(get("/api/citas/{id}",id).header("Authorization",otherVet)).andExpect(status().isForbidden());
 }
 @Test void reservasDeDosPropietariosCompitenPorUnSoloIntervalo() throws Exception {
  long u=user("PROPIETARIO","secondOwner");long p=jdbc.queryForObject("insert into propietario(id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,u);
  long pet2=jdbc.queryForObject("insert into mascota(id_propietario,nombre,especie) values (?,'Segundo paciente sintético','Canino') returning id_mascota",Long.class,p);String token2=login("secondOwner");
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try{var first=pool.submit(()->{gate.await();return reservar("2030-01-11T15:00:00",vetId,owner).andReturn().getResponse().getStatus();});
   var second=pool.submit(()->{gate.await();return mvc.perform(post("/api/citas").header("Authorization",token2).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idMascota",pet2,"idVeterinario",vetId,"fechaCita","2030-01-11T15:00:00","motivoCita","Reserva concurrente sintética")))).andReturn().getResponse().getStatus();});gate.countDown();var codes=new ArrayList<>(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,409),codes);
  }finally{pool.shutdownNow();}
 }
 @Test void reprogramacionExcluyeSoloCitaPropiaYValidaHorario() throws Exception {
  long c=cita("2030-01-11T10:00:00","PENDIENTE");
  mvc.perform(get("/api/citas/disponibilidad").param("idVeterinario",""+vetId).param("fecha","2030-01-11").param("excluirCita",""+c).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.horarios[4].disponible").value(true));
  mvc.perform(put("/api/citas/{id}",c).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"fechaCita\":\"2030-01-11T21:00:00\",\"motivoCita\":\"Cambio inválido\"}")).andExpect(status().isBadRequest());
  assertEquals("2030-01-11T10:00",jdbc.queryForObject("select fecha_cita from cita where id_cita=?",java.sql.Timestamp.class,c).toLocalDateTime().toString());
 }
 private ResultActions reservar(String fecha,long profesional,String token) throws Exception {return mvc.perform(post("/api/citas").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idMascota",petId,"idVeterinario",profesional,"fechaCita",fecha,"motivoCita","Reserva sintética"))));}
 @Test void citaFuturaRechazaInicioYRegistroInclusoAdmin() throws Exception {long c=cita("2030-01-11T10:00:00","CONFIRMADA");for(String token:List.of(vet,admin)){
  mvc.perform(post("/api/citas/{id}/iniciar-atencion",c).header("Authorization",token)).andExpect(status().isConflict());registro(c,token,null).andExpect(status().isConflict());}assertEquals(0,count(c));}
 @Test void horarioPermiteGuardarSinPrecioYActualizaVistasAutorizadas() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);long a=json.readTree(registro(c,vet,null).andExpect(status().isOk()).andExpect(jsonPath("$.precioAtencion").isEmpty()).andReturn().getResponse().getContentAsByteArray()).get("idAtencion").asLong();
  mvc.perform(get("/api/citas/{id}",c).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATENDIDA"));
  mvc.perform(get("/api/atenciones/{id}",a).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.idHistoria").value(history));assertEquals(1,count(c));}
 @Test void formularioIniciadoTiene15MinutosDeToleranciaSinExtension() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);clock.at("2030-01-10T10:45:00");registro(c,vet,null).andExpect(status().isOk());}
 @Test void toleranciaRequiereInicioDuranteHorario() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");clock.at("2030-01-10T10:31:00");registro(c,vet,null).andExpect(status().isConflict());mvc.perform(post("/api/citas/{id}/iniciar-atencion",c).header("Authorization",vet)).andExpect(status().isConflict());}
 @Test void fueraDeToleranciaNoPermiteModalIndefinido() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);clock.at("2030-01-10T10:45:01");registro(c,vet,null).andExpect(status().isConflict());assertEquals(0,count(c));}
 @Test void aperturaAjenaNoConcedeTolerancia() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);clock.at("2030-01-10T10:31:00");registro(c,admin,null).andExpect(status().isConflict());}
 @Test void adminAutorizaUnaCitaYElPermisoSeConsumeConTrazabilidad() throws Exception {long c=cita("2030-01-09T10:00:00","CONFIRMADA"),other=cita("2030-01-08T10:00:00","CONFIRMADA");long r=solicitud(c);resolver(r,true,admin).andExpect(status().isOk()).andExpect(jsonPath("$.idAdministrador").isNumber()).andExpect(jsonPath("$.venceEn").exists());
  registro(other,vet,null).andExpect(status().isConflict());registro(c,vet,null).andExpect(status().isOk());registro(c,vet,null).andExpect(status().isConflict());assertEquals("CONSUMIDA",jdbc.queryForObject("select estado from regularizacion_atencion where id_regularizacion=?",String.class,r));}
 @Test void rechazoYVencimientoBloqueanRegistro() throws Exception {long c=cita("2030-01-09T10:00:00","CONFIRMADA"),r=solicitud(c);resolver(r,false,admin).andExpect(status().isOk());registro(c,vet,null).andExpect(status().isConflict());long r2=solicitud(c);resolver(r2,true,admin).andExpect(status().isOk());clock.at("2030-01-10T11:10:00");registro(c,vet,null).andExpect(status().isConflict());mvc.perform(get("/api/citas/regularizaciones").header("Authorization",vet)).andExpect(status().isOk()).andExpect(jsonPath("$[0].estado").value("EXPIRADA"));}
 @Test void noDuplicaSolicitudPendienteNiPermiteAutoAprobacion() throws Exception {long c=cita("2030-01-09T10:00:00","CONFIRMADA"),r=solicitud(c);pedir(c,vet).andExpect(status().isConflict());resolver(r,true,vet).andExpect(status().isForbidden());resolver(r,true,owner).andExpect(status().isForbidden());pedir(c,otherVet).andExpect(status().isForbidden());mvc.perform(get("/api/citas/regularizaciones").header("Authorization",owner)).andExpect(status().isForbidden());}
 @Test void inasistenciaSoloDespuesDelFinalYPorParticipantesAutorizados() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");noAsistio(c,vet).andExpect(status().isConflict());clock.at("2030-01-10T10:30:00");noAsistio(c,owner).andExpect(status().isForbidden());noAsistio(c,otherVet).andExpect(status().isForbidden());noAsistio(c,vet).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("NO_ASISTIO"));registro(c,vet,null).andExpect(status().isConflict());pedir(c,vet).andExpect(status().isConflict());}
 @ParameterizedTest @ValueSource(strings={"CANCELADA","NO_ASISTIO","ATENDIDA"}) void estadosCerradosNoAdmitenRegistroRegularizacionCancelacionNiReprogramacion(String state) throws Exception {long c=cita("2030-01-09T10:00:00",state);registro(c,vet,null).andExpect(status().isConflict());pedir(c,vet).andExpect(status().isConflict());cancelar(c,admin).andExpect(status().isConflict());mvc.perform(put("/api/citas/{id}",c).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"fechaCita\":\"2030-01-11T10:00:00\",\"motivoCita\":\"Cambio\"}")).andExpect(status().isConflict());}
 @Test void cancelacionGuardaMotivoActorRolYFechaYSeConsultaPrivadamente() throws Exception {long c=cita("2030-01-11T10:00:00","CONFIRMADA");cancelar(c,owner).andExpect(status().isOk()).andExpect(jsonPath("$.motivoCancelacion").value("Otro motivo.")).andExpect(jsonPath("$.detalleCancelacion").value("Motivo sintético")).andExpect(jsonPath("$.rolCancelacion").value("PROPIETARIO")).andExpect(jsonPath("$.fechaCancelacion").value("2030-01-10T10:10:00"));
  mvc.perform(get("/api/citas/{id}",c).header("Authorization",vet)).andExpect(status().isOk()).andExpect(jsonPath("$.idUsuarioCancelacion").isNumber());mvc.perform(get("/api/citas/{id}",c).header("Authorization",otherVet)).andExpect(status().isForbidden());}
 @Test void motivoEsObligatorioYVeterinarioSoloCancelaAsignadas() throws Exception {long c=cita("2030-01-11T10:00:00","PENDIENTE");mvc.perform(put("/api/citas/{id}/cancelar",c).header("Authorization",vet)).andExpect(status().isBadRequest());mvc.perform(put("/api/citas/{id}/cancelar",c).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Otro motivo.\"}")).andExpect(status().isBadRequest());cancelar(c,otherVet).andExpect(status().isForbidden());cancelar(c,vet).andExpect(status().isOk());}
 @Test void confirmacionPermaneceReservadaAAdminOAsignado() throws Exception {long c=cita("2030-01-11T10:00:00","PENDIENTE");for(String t:List.of(owner,otherVet))mvc.perform(put("/api/citas/{id}/confirmar",c).header("Authorization",t)).andExpect(status().isForbidden());mvc.perform(put("/api/citas/{id}/confirmar",c).header("Authorization",vet)).andExpect(status().isOk());long c2=cita("2030-01-12T10:00:00","PENDIENTE");mvc.perform(put("/api/citas/{id}/confirmar",c2).header("Authorization",admin)).andExpect(status().isOk());}
 @Test void precioEsAdministrativoYNoSeBorraAlEditarClinica() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");registro(c,vet,10).andExpect(status().isForbidden());long a=json.readTree(registro(c,vet,null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("idAtencion").asLong();
  for(String t:List.of(vet,owner))mvc.perform(patch("/api/atenciones/{id}/precio",a).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"precioAtencion\":35.50}")).andExpect(status().isForbidden());
  mvc.perform(patch("/api/atenciones/{id}/precio",a).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"precioAtencion\":35.50}")).andExpect(status().isOk());
  var body=payload(c,null);body.remove("idCita");body.remove("idVeterinario");body.remove("idHistoria");mvc.perform(put("/api/atenciones/{id}",a).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.precioAtencion").value(35.5));body.put("precioAtencion",9);mvc.perform(put("/api/atenciones/{id}",a).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isForbidden());}
 @Test void citaAjenaYRelacionClinicaManipuladaSeRechazan() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");registro(c,otherVet,null).andExpect(status().isForbidden());var b=payload(c,null);b.put("idHistoria",999999);mvc.perform(post("/api/atenciones").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b))).andExpect(status().isNotFound());assertEquals(0,count(c));}
 @Test void dosRegistrosConcurrentesProducenUnaSolaAtencion() throws Exception {long c=cita("2030-01-10T10:00:00","CONFIRMADA");var statuses=paralelo(()->registro(c,vet,null).andReturn().getResponse().getStatus());assertEquals(List.of(200,409),statuses);assertEquals(1,count(c));}
 @Test void reservaSuperpuestaConcurrenteSeSerializaYRechazaPasadosEnLima() throws Exception {String body=json.writeValueAsString(Map.of("idVeterinario",vetId,"idMascota",petId,"fechaCita","2030-01-11T10:00:00","motivoCita","Control sintético"));var statuses=paralelo(()->mvc.perform(post("/api/citas").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());assertEquals(List.of(200,409),statuses);mvc.perform(post("/api/citas").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(body.replace("2030-01-11","2030-01-09"))).andExpect(status().isBadRequest());}
 @ParameterizedTest @ValueSource(strings={"PENDIENTE","CONFIRMADA"})
 void propietarioNoPuedeCancelarNiReprogramarCitasIniciadasOVencidas(String estado) throws Exception {
  for(String fecha:List.of("2030-01-10T10:00:00","2030-01-09T10:00:00")) {
   long c=cita(fecha,estado);var before=jdbc.queryForMap("select * from cita where id_cita=?",c);
   mvc.perform(get("/api/citas/{id}",c).header("Authorization",owner)).andExpect(status().isOk())
    .andExpect(jsonPath("$.puedeCancelar").value(false)).andExpect(jsonPath("$.puedeReprogramar").value(false));
   cancelar(c,owner).andExpect(status().isConflict());
   mvc.perform(put("/api/citas/{id}",c).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
    .content("{\"fechaCita\":\"2030-01-12T10:00:00\",\"motivoCita\":\"Reprogramación no autorizada\"}"))
    .andExpect(estado.equals("PENDIENTE")?status().isConflict():status().isForbidden());
   assertEquals(before,jdbc.queryForMap("select * from cita where id_cita=?",c));
  }
 }
 @Test void propietarioConservaGestionDePendienteFutura() throws Exception {
  long c=cita("2030-01-11T10:00:00","PENDIENTE");
  mvc.perform(get("/api/citas/{id}",c).header("Authorization",owner)).andExpect(status().isOk())
   .andExpect(jsonPath("$.puedeCancelar").value(true)).andExpect(jsonPath("$.puedeReprogramar").value(true));
  mvc.perform(put("/api/citas/{id}",c).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
   .content("{\"fechaCita\":\"2030-01-12T10:00:00\",\"motivoCita\":\"Cambio futuro permitido\"}"))
   .andExpect(status().isOk());cancelar(c,owner).andExpect(status().isOk());
 }
 @Test void antecedentesCompartidosDesdeConsultaNoDuplicanExpedienteNiTransformanDiagnostico() throws Exception {
  long c=cita("2030-01-10T10:00:00","CONFIRMADA");
  mvc.perform(get("/api/historias-clinicas/mascota/{id}",petId).header("Authorization",vet))
   .andExpect(status().isOk()).andExpect(jsonPath("$.antecedentes.estadoAlergias").value("NO_EVALUADAS"));
  String data=json.writeValueAsString(Map.of("version",0,"estadoAlergias","ALERGIAS_REGISTRADAS","alergiasSustancias","Penicilina de prueba", "alergiasReacciones","Reacción sintética", "enfermedadesRelevantes","Antecedente clínico sintético"));
  for(String token:List.of(owner,admin,otherVet))
   mvc.perform(put("/api/historias-clinicas/{id}/antecedentes",history).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isForbidden());
  mvc.perform(put("/api/historias-clinicas/{id}/antecedentes",history).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(data))
   .andExpect(status().isOk()).andExpect(jsonPath("$.antecedentes.actualizadoEn").exists()).andExpect(jsonPath("$.antecedentes.veterinarioNombre").isNotEmpty());
  registro(c,vet,null).andExpect(status().isOk());
  for(String token:List.of(owner,vet))
   mvc.perform(get("/api/historias-clinicas/{id}",history).header("Authorization",token)).andExpect(status().isOk())
    .andExpect(jsonPath("$.antecedentes.alergiasSustancias").value("Penicilina de prueba"))
    .andExpect(jsonPath("$.antecedentes.enfermedadesRelevantes").value("Antecedente clínico sintético"));
  assertEquals(1,jdbc.queryForObject("select count(*) from historia_clinica where id_mascota=?",Integer.class,petId));
  assertEquals(1,count(c));
 }
 private List<Integer> paralelo(Callable<Integer> action)throws Exception {try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<Integer> task=()->{start.await();return action.call();};var a=pool.submit(task);var b=pool.submit(task);start.countDown();var result=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(result);return result;}}
 private long user(String role,String name){return jdbc.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Prueba','Citas','PRUEBA',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,suffix+name,name+suffix+"@example.invalid",passwords.encode(password),role);}
 private String login(String name)throws Exception{return "Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",name+suffix+"@example.invalid","contrasena",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("token").asText();}
 @Test void vacunacionClinicaAtomicaConMultiplesDosisYLecturaDelPropietario() throws Exception {
  long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);
  var body=payload(c,null);body.put("vacunas",List.of(
   Map.of("aplicada",true,"nombre","Antirrábica sintética","fechaAplicacion","2030-01-10","proximaDosis","2030-03-10","observaciones","Indicación áéíóúñ"),
   Map.of("aplicada",true,"nombre","Séxtuple sintética","fechaAplicacion","2030-01-10"),
   Map.of("aplicada",false,"nombre","Futura sintética","proximaDosis","2030-04-10")));
  var response=mvc.perform(post("/api/atenciones").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
  long attention=json.readTree(response.getResponse().getContentAsByteArray()).get("idAtencion").asLong();
  assertEquals(2,jdbc.queryForObject("select count(*) from vacuna where id_atencion=?",Integer.class,attention));
  assertEquals(2,jdbc.queryForObject("select count(*) from vacuna where id_mascota=? and fecha_aplicacion is null",Integer.class,petId));
  assertEquals(1,jdbc.queryForObject("select count(*) from vacuna where id_mascota=? and id_vacuna_origen is not null",Integer.class,petId));
  mvc.perform(get("/api/vacunas/mascota/{id}",petId).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
  mvc.perform(get("/api/atenciones/{id}",attention).header("Authorization",owner)).andExpect(status().isOk());
  mvc.perform(get("/api/historias-clinicas/mascota/{id}",petId).header("Authorization",owner)).andExpect(status().isOk());
 }
 @Test void dosisProgramadaConservaIdentidadFechaOriginalYNoDuplicaAlReintentar() throws Exception {
  long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);
  var scheduled=mvc.perform(post("/api/vacunas").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idMascota",petId,"idVeterinario",vetId,"nombre","Dosis sintética","proximaDosis","2030-01-10","observaciones","Indicación original")))).andExpect(status().isOk()).andReturn();
  long id=json.readTree(scheduled.getResponse().getContentAsByteArray()).get("idVacuna").asLong();
  var body=payload(c,null);body.put("vacunas",List.of(Map.of("aplicada",true,"idVacunaProgramada",id,"fechaAplicacion","2030-01-10")));
  var response=mvc.perform(post("/api/atenciones").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
  long attention=json.readTree(response.getResponse().getContentAsByteArray()).get("idAtencion").asLong();
  mvc.perform(get("/api/vacunas/{id}",id).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.fechaProgramada").value("2030-01-10")).andExpect(jsonPath("$.fechaAplicacion").value("2030-01-10")).andExpect(jsonPath("$.idAtencion").value(attention)).andExpect(jsonPath("$.observaciones").value("Indicación original"));
  mvc.perform(post("/api/atenciones").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isConflict());
  mvc.perform(put("/api/vacunas/{id}/aplicar",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idAtencion",attention,"fechaAplicacion","2030-01-10")))).andExpect(status().isConflict());
  assertEquals(1,count(c));assertEquals(1,jdbc.queryForObject("select count(*) from vacuna where id_mascota=?",Integer.class,petId));
 }
 @Test void vacunaInvalidaRevierteAtencionCitaYVacunasPreviasDelMismoEnvio() throws Exception {
  long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);
  var original=jdbc.queryForMap("select * from cita where id_cita=?",c);
  var body=payload(c,null);body.put("vacunas",List.of(Map.of("aplicada",true,"nombre","Válida","fechaAplicacion","2030-01-10"),Map.of("aplicada",true,"nombre","Inválida","fechaAplicacion","2030-01-11")));
  mvc.perform(post("/api/atenciones").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  assertEquals(0,count(c));assertEquals(original,jdbc.queryForMap("select * from cita where id_cita=?",c));
  assertEquals(0,jdbc.queryForObject("select count(*) from vacuna where id_mascota=?",Integer.class,petId));
 }
 @Test void vacunacionIntegradaNoAmpliaPermisosDeAdminPropietarioUOtroVet() throws Exception {
  long c=cita("2030-01-10T10:00:00","CONFIRMADA");inicio(c);
  var body=payload(c,null);body.put("vacunas",List.of(Map.of("aplicada",true,"nombre","Control de permisos","fechaAplicacion","2030-01-10")));
  for(String token:List.of(owner,otherVet,admin))mvc.perform(post("/api/atenciones").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isForbidden());
  assertEquals(0,count(c));assertEquals(0,jdbc.queryForObject("select count(*) from vacuna where id_mascota=?",Integer.class,petId));
 }
 private long cita(String date,String state){return jdbc.queryForObject("insert into cita(id_veterinario,id_mascota,fecha_cita,estado,motivo_cita) values (?,?,?,?, 'Control sintético') returning id_cita",Long.class,vetId,petId,LocalDateTime.parse(date),state);}
 private int count(long c){return jdbc.queryForObject("select count(*) from atenciones where id_cita=?",Integer.class,c);}
 private Map<String,Object> payload(long c,Integer precio){var p=new HashMap<String,Object>();p.put("idCita",c);p.put("idHistoria",history);p.put("idVeterinario",vetId);p.put("peso",12.5);for(String f:List.of("sintoma","diagnostico","tratamiento","observacion"))p.put(f,"Evaluación sintética áéíóúñ");if(precio!=null)p.put("precioAtencion",precio);return p;}
 private ResultActions registro(long c,String t,Integer precio)throws Exception{return mvc.perform(post("/api/atenciones").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload(c,precio))));}
 private void inicio(long c)throws Exception{mvc.perform(post("/api/citas/{id}/iniciar-atencion",c).header("Authorization",vet)).andExpect(status().isOk());}
 private ResultActions pedir(long c,String t)throws Exception{return mvc.perform(post("/api/citas/{id}/regularizaciones",c).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Olvido sintético documentado\"}"));}
 private long solicitud(long c)throws Exception{return json.readTree(pedir(c,vet).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("idRegularizacion").asLong();}
 private ResultActions resolver(long r,boolean approve,String t)throws Exception{return mvc.perform(put("/api/citas/regularizaciones/{id}/resolver",r).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":"+approve+"}"));}
 private ResultActions noAsistio(long c,String t)throws Exception{return mvc.perform(put("/api/citas/{id}/no-asistio",c).header("Authorization",t));}
 private ResultActions cancelar(long c,String t)throws Exception{return mvc.perform(put("/api/citas/{id}/cancelar",c).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Otro motivo.\",\"descripcion\":\"Motivo sintético\"}"));}
}
