package pe.edu.utec.vetcare.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminHttpIT extends PostgresIntegrationSupport {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder; @Autowired ObjectMapper json;
 String password,key,admin,vet,owner;long adminId,vetId,vetUser,ownerId,pet,history,cita,attention;
 @BeforeEach void prepare() throws Exception {
  assertEquals("vetcare_p0_integration",db.queryForObject("select current_database()",String.class));
  password="Qa!"+UUID.randomUUID().toString();key=UUID.randomUUID().toString().substring(0,10);
  adminId=user("ADMIN","admin");vetUser=user("VETERINARIO","vet");long own=user("PROPIETARIO","owner");
  vetId=db.queryForObject("insert into veterinario(id_usuario,especialidad,nro_colegiatura) values (?,'General',?) returning id_veterinario",Long.class,vetUser,key);
  ownerId=db.queryForObject("insert into propietario(id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,own);
  pet=db.queryForObject("insert into mascota(id_propietario,nombre,especie) values (?,'Paciente ADMIN sintético','Canina') returning id_mascota",Long.class,ownerId);
  history=db.queryForObject("insert into historia_clinica(id_mascota,fecha_apertura) values (?,current_timestamp) returning id_historia",Long.class,pet);
  cita=db.queryForObject("insert into cita(id_mascota,id_veterinario,fecha_cita,estado) values (?,?,current_timestamp,'ATENDIDA') returning id_cita",Long.class,pet,vetId);
  attention=db.queryForObject("insert into atenciones(id_historia,id_veterinario_atencion,id_cita,fecha_atencion,peso,sintoma,diagnostico,tratamiento) values (?,?,?,current_timestamp,12,'Síntoma original','Diagnóstico original','Tratamiento original') returning id_atencion",Long.class,history,vetId,cita);
  admin=login("admin");vet=login("vet");owner=login("owner");
 }
 @Test void jwtAdminConsultaGlobalYCuentasNoExponenCredenciales() throws Exception {
  mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
  for(String t:List.of(vet,owner))mvc.perform(get("/api/usuarios").header("Authorization",t)).andExpect(status().isForbidden());
  var r=mvc.perform(get("/api/usuarios").header("Authorization",admin)).andExpect(status().isOk()).andReturn();
  for(var u:json.readTree(r.getResponse().getContentAsByteArray())){assertFalse(u.has("contrasena"));assertFalse(u.has("password"));}
  for(String path:List.of("/api/propietarios","/api/mascotas","/api/citas","/api/atenciones","/api/veterinarios"))mvc.perform(get(path).header("Authorization",admin)).andExpect(status().isOk());
  mvc.perform(get("/api/usuarios/me").header("Authorization",admin)).andExpect(jsonPath("$.rol").value("ADMIN"));
 }
 @Test void adminRegistraYEditaTratamientoQueVeterinarioConsultaSinPoderAsignarselo() throws Exception {
  var body=registration();var r=postJson("/api/veterinarios/registro",admin,body).andExpect(status().is2xxSuccessful()).andReturn();
  long id=json.readTree(r.getResponse().getContentAsByteArray()).get("idVeterinario").asLong();
  String token=login("new");mvc.perform(get("/api/veterinarios/me").header("Authorization",token)).andExpect(jsonPath("$.tratamientoProfesional").value("Dr."));
  String hash=db.queryForObject("select contrasena from usuario where correo=?",String.class,email("new"));assertTrue(encoder.matches(password,hash));assertNotEquals(password,hash);
  putJson("/api/veterinarios/"+id,admin,Map.of("especialidad","General actualizada")).andExpect(status().isOk()).andExpect(jsonPath("$.tratamientoProfesional").value("Dr."));
  putJson("/api/veterinarios/"+id,admin,Map.of("especialidad","Cirugía","tratamientoProfesional","Dra.","datosUsuario",Map.of("nroCelular","999111222","direccion","Dirección actualizada"))).andExpect(status().isOk());
  mvc.perform(get("/api/veterinarios/me").header("Authorization",token)).andExpect(jsonPath("$.tratamientoProfesional").value("Dra."));
  mvc.perform(patch("/api/veterinarios/me").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"nroCelular\":\"999888777\",\"direccion\":\"Dirección sintética\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.tratamientoProfesional").value("Dra."));
  mvc.perform(patch("/api/veterinarios/me").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"tratamientoProfesional\":\"Dr.\"}")).andExpect(status().isBadRequest());
  putJson("/api/veterinarios/"+id,token,Map.of("especialidad","General","nroColegiatura","NEW-"+key,"tratamientoProfesional","Dr.")).andExpect(status().isForbidden());
  putJson("/api/veterinarios/"+id,admin,Map.of("especialidad","Cirugía","tratamientoProfesional","")).andExpect(status().isOk()).andExpect(jsonPath("$.tratamientoProfesional").isEmpty());
 }
 @Test void validacionDeTratamientoDuplicadosYEdicionAtomica() throws Exception {
  var body=registration();body.put("tratamientoProfesional","DRA");postJson("/api/veterinarios/registro",admin,body).andExpect(status().isBadRequest());
  assertEquals(0,db.queryForObject("select count(*) from usuario where correo=?",Integer.class,email("new")));
  body.put("tratamientoProfesional","Dr.");body.put("correo",email("vet"));postJson("/api/veterinarios/registro",admin,body).andExpect(status().isBadRequest());
  var before=db.queryForMap("select * from veterinario where id_veterinario=?",vetId);
  var identity=Map.of("nombres","Nuevo","apellidos","Nombre","tipoDocumento","PRUEBA","nroDocumento","NEW-"+key,"correo",email("owner"));
  putJson("/api/veterinarios/"+vetId,admin,Map.of("especialidad","Cambio","nroColegiatura",key,"tratamientoProfesional","Dr.","datosUsuario",identity)).andExpect(status().isBadRequest());
  assertEquals(before,db.queryForMap("select * from veterinario where id_veterinario=?",vetId));
 }
 @Test void adminConsultaPeroNoReescribeNiEliminaAtencionYConservaAsignacionDePrecio() throws Exception {
  var original=db.queryForMap("select * from atenciones where id_atencion=?",attention);
  putJson("/api/atenciones/"+attention,admin,Map.of("peso",13,"sintoma","Cambio","diagnostico","Cambio","tratamiento","Cambio")).andExpect(status().isForbidden());
  mvc.perform(delete("/api/atenciones/{id}",attention).header("Authorization",admin)).andExpect(status().isConflict());
  assertEquals(original,db.queryForMap("select * from atenciones where id_atencion=?",attention));
  mvc.perform(patch("/api/atenciones/{id}/precio",attention).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"precioAtencion\":50}")).andExpect(status().isOk());
  assertEquals("Diagnóstico original",db.queryForObject("select diagnostico from atenciones where id_atencion=?",String.class,attention));
  mvc.perform(delete("/api/citas/{id}",cita).header("Authorization",admin)).andExpect(status().isConflict());
 }
 @Test void contactoAdminEsPropioYNoPermiteIdentidadNiRol() throws Exception {
  mvc.perform(patch("/api/usuarios/me").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"nroCelular\":\"999888777\",\"direccion\":\"Dirección administrativa\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.direccionContacto").value("Dirección administrativa"));
  for(String t:List.of(vet,owner))mvc.perform(patch("/api/usuarios/me").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"nroCelular\":\"999888777\"}")).andExpect(status().isForbidden());
  mvc.perform(patch("/api/usuarios/me").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"nombres\":\"Otro\",\"idRol\":2}")).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/usuarios/{id}",adminId).header("Authorization",admin)).andExpect(status().isBadRequest());
  db.update("update usuario set correo=? where id_usuario=?",email("admin-modificado"),adminId);
  // El JWT queda inválido al cambiar la identidad de correo.
  mvc.perform(delete("/api/usuarios/{id}",adminId).header("Authorization",admin)).andExpect(status().isUnauthorized());
 }
 @Test void contactoAdminRechazaMovilesInvalidosSinCambiarDatos() throws Exception {
  var original=db.queryForMap("select nro_celular,direccion_contacto from usuario where id_usuario=?",adminId);
  for(String phone:List.of("", "782912", "899111222", "99911122", "9991112223", "+51999111222", "99911122a")) {
   mvc.perform(patch("/api/usuarios/me").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
     .content(json.writeValueAsString(java.util.Map.of("nroCelular",phone,"direccion","No guardar")))).andExpect(status().isBadRequest());
   assertEquals(original,db.queryForMap("select nro_celular,direccion_contacto from usuario where id_usuario=?",adminId));
  }
 }
 @Test void relacionesImpidenEliminarVeterinarioUsuarioOPropietarioConHistoria() throws Exception {
  for(String path:List.of("/api/veterinarios/"+vetId,"/api/usuarios/"+vetUser,"/api/propietarios/"+ownerId))mvc.perform(delete(path).header("Authorization",admin)).andExpect(status().isConflict());
  assertEquals(1,db.queryForObject("select count(*) from atenciones where id_atencion=?",Integer.class,attention));
 }
 @Test void fotografiaVeterinariaSoloLaGestionaSuTitularYSeConsultaPorPerfil() throws Exception {
  var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
  for(String t:List.of(admin,vet,owner)) {
   mvc.perform(multipart("/api/usuarios/{id}/foto",vetUser).file(new MockMultipartFile("foto","qa.png","image/png",bytes.toByteArray())).header("Authorization",t)).andExpect(status().isForbidden());
   mvc.perform(delete("/api/usuarios/{id}/foto",vetUser).header("Authorization",t)).andExpect(status().isForbidden());
  }
  mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("foto","qa.png","image/png",bytes.toByteArray())).header("Authorization",vet)).andExpect(status().isOk());
  for(String t:List.of(admin,vet,owner))mvc.perform(get("/api/veterinarios/{id}/foto",vetId).header("Authorization",t)).andExpect(status().isOk()).andExpect(content().bytes(bytes.toByteArray())).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get("/api/veterinarios/{id}/foto",vetId)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/veterinarios/{id}/foto",Long.MAX_VALUE).header("Authorization",owner)).andExpect(status().isNotFound());
  mvc.perform(get("/api/usuarios/{id}/foto",adminId).header("Authorization",owner)).andExpect(status().isForbidden());
  mvc.perform(get("/api/usuarios/{id}/foto",vetUser).header("Authorization",owner)).andExpect(status().isForbidden());
  mvc.perform(get("/api/veterinarios/{id}",vetId).header("Authorization",owner)).andExpect(jsonPath("$.fotoDisponible").value(true));
  mvc.perform(delete("/api/usuarios/me/foto").header("Authorization",vet)).andExpect(status().isNoContent());
  mvc.perform(get("/api/veterinarios/{id}/foto",vetId).header("Authorization",owner)).andExpect(status().isNotFound());
  mvc.perform(get("/api/veterinarios/{id}",vetId).header("Authorization",owner)).andExpect(jsonPath("$.fotoDisponible").value(false));
 }
 @Test void gestionDeUsuarioValidaDuplicadosRolesYConservaCredencialesAlEditar() throws Exception {
  long role=db.queryForObject("select id_rol from roles where nombre='PROPIETARIO'",Long.class);
  var body=new HashMap<String,Object>(Map.of("nombres","Usuario","apellidos","Administrado","tipoDocumento","DNI","nroDocumento","U-"+key,"correo",email("account"),"contrasena",password,"idRol",role));
  var result=postJson("/api/usuarios",admin,body).andExpect(status().isCreated()).andExpect(jsonPath("$.contrasena").doesNotExist()).andReturn();
  long id=json.readTree(result.getResponse().getContentAsByteArray()).get("idUsuario").asLong();
  String original=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id);assertTrue(encoder.matches(password,original));
  postJson("/api/usuarios",admin,body).andExpect(status().isBadRequest());
  var edit=new HashMap<String,Object>();edit.put("nroCelular","987654321");edit.put("direccion","Dirección nueva");
  putJson("/api/usuarios/"+id,admin,edit).andExpect(status().isOk());
  assertEquals(original,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id));
  assertEquals(role,db.queryForObject("select id_rol from usuario where id_usuario=?",Long.class,id));
  edit.put("idRol",1);putJson("/api/usuarios/"+id,admin,edit).andExpect(status().isBadRequest());
  // El contrato rechaza explícitamente cambiar el rol: se conservan las vinculaciones.
  assertEquals(role,db.queryForObject("select id_rol from usuario where id_usuario=?",Long.class,id));
  body.put("correo",email("invalid-role"));body.put("nroDocumento","UR-"+key);body.put("idRol",Long.MAX_VALUE);postJson("/api/usuarios",admin,body).andExpect(status().isNotFound());
  mvc.perform(delete("/api/usuarios/{id}",id).header("Authorization",admin)).andExpect(status().isNoContent());
 }
 private Map<String,Object> registration(){return new HashMap<>(Map.of("nombres","Veterinario","apellidos","Sintético","tipoDocumento","PRUEBA","nroDocumento","NEW-"+key,"correo",email("new"),"contrasena",password,"especialidad","General","nroColegiatura","NEW-"+key,"tratamientoProfesional","Dr."));}
 private ResultActions postJson(String path,String token,Object body)throws Exception{return mvc.perform(post(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
 private ResultActions putJson(String path,String token,Object body)throws Exception{return mvc.perform(put(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
 private String email(String name){return "admin-qa-"+name+"-"+key+"@example.invalid";}
 private long user(String role,String name){return db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Cuenta','Sintética','PRUEBA',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,name+key,email(name),encoder.encode(password),role);}
 private String login(String name)throws Exception {var r=postJson("/api/auth/login","",Map.of("correo",email(name),"contrasena",password)).andExpect(status().isOk()).andReturn();return "Bearer "+json.readTree(r.getResponse().getContentAsByteArray()).get("token").asText();}
}
