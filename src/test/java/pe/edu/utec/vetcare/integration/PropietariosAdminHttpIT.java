package pe.edu.utec.vetcare.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PropietariosAdminHttpIT extends PostgresIntegrationSupport {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired PasswordEncoder encoder;@Autowired ObjectMapper json;
 static final Path PHOTOS=tempPhotos();
 static Path tempPhotos(){try{return Files.createTempDirectory("vetcare-owners-photos-");}catch(Exception e){throw new IllegalStateException(e);}}
 @DynamicPropertySource static void photos(DynamicPropertyRegistry r){r.add("vetcare.perfiles.directorio",PHOTOS::toString);}
 String tag,password,admin,owner,vet,other;long adminId,ownerUser,vetUser,otherUser,ownerId,pet,history;
 @BeforeEach void setup()throws Exception{
  assertEquals("vetcare_p0_integration",db.queryForObject("select current_database()",String.class));
  tag=UUID.randomUUID().toString().substring(0,8);password="Qa!7"+tag;
  adminId=account("ADMIN","a");ownerUser=account("PROPIETARIO","o");vetUser=account("VETERINARIO","v");otherUser=account("PROPIETARIO","b");
  ownerId=profile(ownerUser);pet=db.queryForObject("insert into mascota(id_propietario,nombre,especie,raza) values (?,'Paciente sintético','PERRO','Mestizo') returning id_mascota",Long.class,ownerId);
  history=db.queryForObject("insert into historia_clinica(id_mascota,fecha_apertura) values (?,current_timestamp) returning id_historia",Long.class,pet);
  admin=login("a");owner=login("o");vet=login("v");other=login("b");
 }
 long account(String role,String suffix){return db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,nro_celular,id_rol,fecha_registro) values ('Persona','Sintética','DNI',?,?,?,'999111222',(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,"00"+tag+suffix,suffix+tag+"@example.invalid",encoder.encode(password),role);}
 long profile(long user){return db.queryForObject("insert into propietario(id_usuario,direccion,fecha_registro_clinica,estado_cliente) values (?,'Dirección inicial',current_date,'ACTIVO') returning id_propietario",Long.class,user);}
 String login(String suffix)throws Exception{return "Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",suffix+tag+"@example.invalid","contrasena",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asString();}
 org.springframework.test.web.servlet.ResultActions update(String token,Map<String,Object> body)throws Exception{return mvc.perform(put("/api/propietarios/{id}",ownerId).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
 @Test void contactoAdminSePersisteEnFuentesCanonicasYSeReflejaEnPerfiles()throws Exception{
  String hash=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,ownerUser);
  update(admin,Map.of("nroCelular","987654321","direccion","Dirección con acentos: Perú")).andExpect(status().isOk()).andExpect(jsonPath("$.nroCelular").value("987654321"));
  mvc.perform(get("/api/propietarios/me").header("Authorization",owner)).andExpect(jsonPath("$.direccion").value("Dirección con acentos: Perú")).andExpect(jsonPath("$.nroCelular").value("987654321"));
  mvc.perform(get("/api/usuarios/{id}",ownerUser).header("Authorization",admin)).andExpect(jsonPath("$.direccionContacto").value("Dirección con acentos: Perú"));
  mvc.perform(get("/api/usuarios/me").header("Authorization",owner)).andExpect(jsonPath("$.nroCelular").value("987654321")).andExpect(jsonPath("$.direccionContacto").value("Dirección con acentos: Perú"));
  assertEquals(hash,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,ownerUser));
  assertEquals("Persona",db.queryForObject("select nombres from usuario where id_usuario=?",String.class,ownerUser));
  assertEquals(ownerUser,db.queryForObject("select id_usuario from propietario where id_propietario=?",Long.class,ownerId));
  db.update("update usuario set direccion_contacto='Referencia antigua' where id_usuario=?",ownerUser);
  update(admin,Map.of("nroCelular","","direccion","")).andExpect(status().isOk());
  mvc.perform(get("/api/usuarios/{id}",ownerUser).header("Authorization",admin)).andExpect(jsonPath("$.direccionContacto").value(""));
  mvc.perform(get("/api/usuarios/me").header("Authorization",owner)).andExpect(jsonPath("$.nroCelular").value("")).andExpect(jsonPath("$.direccionContacto").value(""));
 }
 @Test void validacionYCamposProtegidosRechazanCambiosAtomicos()throws Exception{
  for(String phone:List.of("abc","887654321","98765432","9876543210","+51987654321"))update(admin,Map.of("nroCelular",phone,"direccion","No guardar")).andExpect(status().isBadRequest());
  update(admin,Map.of("idUsuario",otherUser,"nroCelular","987654321","direccion","No reasociar")).andExpect(status().isBadRequest());
  for(String field:List.of("nombres","apellidos","correo","nroDocumento","idRol","contrasena"))update(admin,Map.of("idUsuario",ownerUser,"direccion","No guardar",field,"No permitido")).andExpect(status().isBadRequest());
  assertEquals("Dirección inicial",db.queryForObject("select direccion from propietario where id_propietario=?",String.class,ownerId));
  assertEquals("999111222",db.queryForObject("select nro_celular from usuario where id_usuario=?",String.class,ownerUser));
 }
 @Test void endpointsAdministrativosNoAdmitenRolesInferiores()throws Exception{
  for(String t:List.of(owner,vet,other)){
   mvc.perform(get("/api/propietarios").header("Authorization",t)).andExpect(status().isForbidden());
   update(t,Map.of("idUsuario",ownerUser,"direccion","No autorizado")).andExpect(status().isForbidden());
   mvc.perform(delete("/api/propietarios/{id}",ownerId).header("Authorization",t)).andExpect(status().isForbidden());
  }
  mvc.perform(get("/api/propietarios")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/propietarios/{id}",ownerId).header("Authorization",other)).andExpect(status().isForbidden());
  mvc.perform(get("/api/mascotas/{id}",pet).header("Authorization",admin)).andExpect(status().isOk());
  mvc.perform(get("/api/historias-clinicas/mascota/{id}",pet).header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.idMascota").value(pet));
 }
 @Test void vinculacionSoloUsaCuentaPropietariaSinPerfilYNoDuplicaUsuario()throws Exception{
  long count=db.queryForObject("select count(*) from usuario",Long.class);
  mvc.perform(post("/api/propietarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idUsuario",otherUser,"direccion","Nueva dirección")))).andExpect(status().isCreated()).andExpect(jsonPath("$.idUsuario").value(otherUser));
  for(long id:List.of(ownerUser,otherUser,vetUser,adminId))mvc.perform(post("/api/propietarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idUsuario",id)))).andExpect(status().isBadRequest());
  assertEquals(count,db.queryForObject("select count(*) from usuario",Long.class));
 }
 @Test void eliminarConMascotaConservaHistoriaYEliminarPerfilVacioConservaCuenta()throws Exception{
  mvc.perform(delete("/api/propietarios/{id}",ownerId).header("Authorization",admin)).andExpect(status().isConflict());
  assertEquals(1,db.queryForObject("select count(*) from mascota where id_mascota=?",Integer.class,pet));
  assertEquals(1,db.queryForObject("select count(*) from historia_clinica where id_historia=?",Integer.class,history));
  long empty=profile(otherUser);mvc.perform(delete("/api/propietarios/{id}",empty).header("Authorization",admin)).andExpect(status().isNoContent());
  assertEquals(1,db.queryForObject("select count(*) from usuario where id_usuario=?",Integer.class,otherUser));
  mvc.perform(delete("/api/propietarios/{id}",Long.MAX_VALUE).header("Authorization",admin)).andExpect(status().isNotFound());
 }
 byte[] image(int color)throws Exception{var image=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,color);var stream=new ByteArrayOutputStream();ImageIO.write(image,"png",stream);return stream.toByteArray();}
 @Test void fotoPropiaSeActualizaEnListadoYLecturaPrivadaAdminSinCache()throws Exception{
  byte[] first=image(0x123456),second=image(0x654321);
  mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("foto","prueba.png","image/png",first)).header("Authorization",owner)).andExpect(status().isOk());
  mvc.perform(get("/api/propietarios/{id}",ownerId).header("Authorization",admin)).andExpect(jsonPath("$.fotoDisponible").value(true));
  mvc.perform(get("/api/usuarios/{id}/foto",ownerUser).header("Authorization",admin)).andExpect(content().bytes(first)).andExpect(header().string("Cache-Control","no-store"));
  for(String t:List.of(other,vet))mvc.perform(get("/api/usuarios/{id}/foto",ownerUser).header("Authorization",t)).andExpect(status().isForbidden());
  mvc.perform(get("/api/usuarios/{id}/foto",ownerUser)).andExpect(status().isUnauthorized());
  mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("foto","prueba.png","image/png",second)).header("Authorization",owner)).andExpect(status().isOk());
  mvc.perform(get("/api/usuarios/{id}/foto",ownerUser).header("Authorization",admin)).andExpect(content().bytes(second));
  mvc.perform(delete("/api/usuarios/me/foto").header("Authorization",owner)).andExpect(status().isNoContent());
  mvc.perform(get("/api/propietarios/{id}",ownerId).header("Authorization",admin)).andExpect(jsonPath("$.fotoDisponible").value(false));
  mvc.perform(get("/api/usuarios/{id}/foto",ownerUser).header("Authorization",admin)).andExpect(status().isNotFound());
 }
 @Test void direccionProfesionalNoSeReemplazaPorUnPerfilPropietarioHistorico()throws Exception{
  db.update("update usuario set direccion_contacto='Dirección profesional' where id_usuario=?",vetUser);
  profile(vetUser); // Simula una vinculación histórica; el flujo nuevo ya no permite crearla.
  mvc.perform(get("/api/usuarios/me").header("Authorization",vet)).andExpect(status().isOk()).andExpect(jsonPath("$.direccionContacto").value("Dirección profesional"));
  mvc.perform(get("/api/usuarios/{id}",vetUser).header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.direccionContacto").value("Dirección profesional"));
 }

}
