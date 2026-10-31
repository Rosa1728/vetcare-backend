package pe.edu.utec.vetcare.integration;

import java.time.*;
import java.util.*;
import java.security.SecureRandom;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import pe.edu.utec.vetcare.service.RecordatorioVacunaService;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Solo vetcare_pruebas_local en localhost. Sin Flyway, SMTP ni scheduler. Cada prueba revierte sus datos sintéticos. */
@SpringBootTest(properties={"spring.config.location=classpath:/application-integration.properties","spring.datasource.url=jdbc:postgresql://localhost:5432/vetcare_pruebas_local","spring.datasource.username=rosapp","spring.datasource.password=","spring.flyway.enabled=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE,printOnlyOnFailure=false)
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="vetcare.local.vacunas",matches="true")
class VacunasLocalHttpIT {
 @TestConfiguration static class Time { @Bean @Primary Clock testClock(){return Clock.fixed(Instant.parse("2026-11-03T15:05:00Z"),ZoneId.of("America/Lima"));} }
 private static final String KEY=key();
 private static String key(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
 @DynamicPropertySource static void secret(DynamicPropertyRegistry r){r.add("JWT_SECRET",()->KEY);}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;@Autowired EntityManager em;@Autowired RecordatorioVacunaService reminders;
 long pet,history,vetId,attention,cita,otherPet;String owner,otherOwner,vet,admin;String password,ownerAccount;LocalDate today=LocalDate.of(2026,11,3);
 @BeforeEach void fixture() throws Exception {
  assertEquals("vetcare_pruebas_local",db.queryForObject("select current_database()",String.class));assertTrue(db.queryForObject("select inet_server_addr() in (inet '::1',inet '127.0.0.1') and inet_server_port()=5432",Boolean.class));
  String tag=UUID.randomUUID().toString().substring(0,10);password=UUID.randomUUID().toString();long o=user("PROPIETARIO","o"+tag),oo=user("PROPIETARIO","x"+tag),v=user("VETERINARIO","v"+tag);user("ADMIN","a"+tag);
  long ownerId=db.queryForObject("insert into propietario(id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,o);
  long otherOwnerId=db.queryForObject("insert into propietario(id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,oo);
  vetId=db.queryForObject("insert into veterinario(id_usuario,especialidad,nro_colegiatura,tratamiento_profesional) values (?,'General',?,'Dr.') returning id_veterinario",Long.class,v,"QA-"+tag);
  pet=db.queryForObject("insert into mascota(id_propietario,nombre,especie) values (?,'Paciente QA','Gato') returning id_mascota",Long.class,ownerId);
  otherPet=db.queryForObject("insert into mascota(id_propietario,nombre,especie) values (?,'Paciente ajeno QA','Perro') returning id_mascota",Long.class,otherOwnerId);
  history=db.queryForObject("insert into historia_clinica(id_mascota,fecha_apertura) values (?,current_timestamp) returning id_historia",Long.class,pet);
  cita=db.queryForObject("insert into cita(id_mascota,id_veterinario,fecha_cita,estado) values (?,?,?,'ATENDIDA') returning id_cita",Long.class,pet,vetId,today.atTime(9,0));
  attention=db.queryForObject("insert into atenciones(id_historia,id_veterinario_atencion,id_cita,fecha_atencion,peso,diagnostico) values (?,?,?,?,4,'Control QA') returning id_atencion",Long.class,history,vetId,cita,today.atTime(9,5));
  ownerAccount="o"+tag;owner=login(ownerAccount);otherOwner=login("x"+tag);vet=login("v"+tag);admin=login("a"+tag);
 }
 @Test void programaDentroDeVentanaGeneraAvisoSoloAlPropietarioCorrectoYNoDuplica() throws Exception {
  long id=schedule(today.plusDays(3));assertEquals(1,count(id));reminders.ejecutarAutomaticamente();reminders.ejecutarAutomaticamente();assertEquals(1,count(id));
  var own=mvc.perform(get("/api/recordatorios/mis").header("Authorization",owner)).andExpect(status().isOk()).andReturn();assertTrue(json.readTree(own.getResponse().getContentAsByteArray()).valueStream().anyMatch(n->n.get("idVacuna").asLong()==id));
  var foreign=mvc.perform(get("/api/recordatorios/mis").header("Authorization",otherOwner)).andExpect(status().isOk()).andReturn();assertFalse(json.readTree(foreign.getResponse().getContentAsByteArray()).valueStream().anyMatch(n->n.get("idVacuna").asLong()==id));
  long reminder=db.queryForObject("select id_recordatorio from recordatorio_vacuna where id_vacuna=?",Long.class,id);
  mvc.perform(patch("/api/recordatorios/{id}/leido",reminder).header("Authorization",otherOwner)).andExpect(status().isForbidden());
 }
 @Test void campanitaConservaLecturaDespuesDeNuevaSesionYRecarga() throws Exception {
  long id=schedule(today.plusDays(1));long reminder=db.queryForObject("select id_recordatorio from recordatorio_vacuna where id_vacuna=?",Long.class,id);
  mvc.perform(patch("/api/recordatorios/{id}/leido",reminder).header("Authorization",owner)).andExpect(status().isOk());em.flush();em.clear();
  owner=login(ownerAccount);var response=mvc.perform(get("/api/recordatorios/mis").header("Authorization",owner)).andExpect(status().isOk()).andReturn();
  assertTrue(json.readTree(response.getResponse().getContentAsByteArray()).valueStream().anyMatch(n->n.get("idVacuna").asLong()==id&&n.get("leido").asBoolean()));
 }
 @Test void edicionRecalculaSinBorrarAvisosAnterioresYLosLeidosPersisten() throws Exception {
  long id=schedule(today.plusDays(3));long reminder=db.queryForObject("select id_recordatorio from recordatorio_vacuna where id_vacuna=?",Long.class,id);
  mvc.perform(patch("/api/recordatorios/{id}/leido",reminder).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.leido").value(true));
  mvc.perform(put("/api/vacunas/{id}/programacion",id).header("Authorization",vet).contentType("application/json").content(json.writeValueAsString(Map.of("nombre","Antirrábica editada","proximaDosis",today.plusDays(1).toString(),"observaciones","Indicaciones QA")))).andExpect(status().isOk());
  em.flush();em.clear();assertEquals(2,count(id));assertNotNull(db.queryForObject("select leido_en from recordatorio_vacuna where id_recordatorio=?",java.sql.Timestamp.class,reminder));
  var r=mvc.perform(get("/api/recordatorios/mis").header("Authorization",owner)).andExpect(status().isOk()).andReturn();assertEquals(1,json.readTree(r.getResponse().getContentAsByteArray()).valueStream().filter(n->n.get("idVacuna").asLong()==id).count());
  assertEquals("Indicaciones QA",db.queryForObject("select observaciones from vacuna where id_vacuna=?",String.class,id));
 }
 @ParameterizedTest @ValueSource(ints={0,1}) void aplicaConservandoFechaProgramadaAtencionYRecordatoriosSinDuplicar(int diasDespues) throws Exception {
  long id=schedule(today);LocalDate planned=today.minusDays(diasDespues);if(diasDespues>0){db.update("update vacuna set fecha_programada=?,proxima_dosis=? where id_vacuna=?",planned,planned,id);em.clear();}apply(id,vet).andExpect(status().isOk());em.flush();em.clear();
  assertEquals(planned,db.queryForObject("select fecha_programada from vacuna where id_vacuna=?",LocalDate.class,id));assertEquals(today,db.queryForObject("select fecha_aplicacion from vacuna where id_vacuna=?",LocalDate.class,id));assertEquals(attention,db.queryForObject("select id_atencion from vacuna where id_vacuna=?",Long.class,id));
  reminders.ejecutarAutomaticamente();assertEquals(1,count(id));assertEquals(0,json.readTree(mvc.perform(get("/api/recordatorios/mis").header("Authorization",owner)).andReturn().getResponse().getContentAsByteArray()).valueStream().filter(n->n.get("idVacuna").asLong()==id).count());
  apply(id,vet).andExpect(status().isConflict());
 }
 @Test void nuevaAtencionConfirmaCitaYAplicacionEnLaTransaccionClinicaExistente() throws Exception {
  long id=schedule(today);long current=db.queryForObject("insert into cita(id_mascota,id_veterinario,fecha_cita,estado) values (?,?,?,'CONFIRMADA') returning id_cita",Long.class,pet,vetId,today.atTime(10,0));
  mvc.perform(post("/api/citas/{id}/iniciar-atencion",current).header("Authorization",vet)).andExpect(status().isOk());
  var body=Map.of("idCita",current,"idHistoria",history,"idVeterinario",vetId,"peso",4,"sintoma","Control preventivo","diagnostico","Evaluación QA","tratamiento","Vacunación QA","vacunas",List.of(Map.of("aplicada",true,"idVacunaProgramada",id,"fechaAplicacion",today.toString())));
  mvc.perform(post("/api/atenciones").header("Authorization",vet).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
  em.flush();em.clear();assertEquals("ATENDIDA",db.queryForObject("select estado from cita where id_cita=?",String.class,current));
  long saved=db.queryForObject("select id_atencion from atenciones where id_cita=?",Long.class,current);assertEquals(saved,db.queryForObject("select id_atencion from vacuna where id_vacuna=?",Long.class,id));assertEquals(today,db.queryForObject("select fecha_programada from vacuna where id_vacuna=?",LocalDate.class,id));
 }
 @Test void rechazaAplicacionAnteriorALaProgramacion() throws Exception {long id=schedule(today.plusDays(1));apply(id,vet).andExpect(status().isBadRequest());assertNull(db.queryForObject("select fecha_aplicacion from vacuna where id_vacuna=?",LocalDate.class,id));}
 @ParameterizedTest @ValueSource(strings={"CANCELADA","NO_ASISTIO","CONFIRMADA"}) void rechazaAtencionDeCitaEnEstadoIncompatible(String state) throws Exception {long id=schedule(today);db.update("update cita set estado=? where id_cita=?",state,cita);em.clear();apply(id,vet).andExpect(status().isBadRequest());}
 @Test void permisosYExpedienteAjenoSeRechazan() throws Exception {long id=schedule(today);apply(id,owner).andExpect(status().isForbidden());apply(id,admin).andExpect(status().isForbidden());db.update("update cita set id_mascota=? where id_cita=?",otherPet,cita);em.clear();apply(id,vet).andExpect(status().isBadRequest());}
 private int count(long id){return db.queryForObject("select count(*) from recordatorio_vacuna where id_vacuna=?",Integer.class,id);}
 private long schedule(LocalDate day)throws Exception {var r=mvc.perform(post("/api/vacunas").header("Authorization",vet).contentType("application/json").content(json.writeValueAsString(Map.of("idMascota",pet,"idVeterinario",vetId,"nombre","Antirrábica QA","proximaDosis",day.toString())))).andExpect(status().isOk()).andReturn();return json.readTree(r.getResponse().getContentAsByteArray()).get("idVacuna").asLong();}
 private org.springframework.test.web.servlet.ResultActions apply(long id,String token)throws Exception{return mvc.perform(put("/api/vacunas/{id}/aplicar",id).header("Authorization",token).contentType("application/json").content(json.writeValueAsString(Map.of("idAtencion",attention,"fechaAplicacion",today.toString()))));}
 private long user(String role,String name){return db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('QA','Vacunas','PRUEBA',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,UUID.randomUUID().toString().substring(0,18),name+"@example.invalid",encoder.encode(password),role);}
 private String login(String name)throws Exception {var r=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("correo",name+"@example.invalid","contrasena",password)))).andExpect(status().isOk()).andReturn();return "Bearer "+json.readTree(r.getResponse().getContentAsByteArray()).get("token").asText();}
}
