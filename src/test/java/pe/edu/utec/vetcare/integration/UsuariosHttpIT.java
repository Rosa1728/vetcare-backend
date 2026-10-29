package pe.edu.utec.vetcare.integration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class UsuariosHttpIT extends PostgresIntegrationSupport {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder; @Autowired ObjectMapper json; @Autowired pe.edu.utec.vetcare.service.UsuarioService usuarios;
 String tag,password,admin,owner,vet;long adminId,ownerId,vetId,roleAdmin,roleOwner;
 @BeforeEach void setup()throws Exception{assertEquals("vetcare_p0_integration",db.queryForObject("select current_database()",String.class));tag=UUID.randomUUID().toString().substring(0,8);password="Qa!7"+tag;roleAdmin=role("ADMIN");roleOwner=role("PROPIETARIO");adminId=account("ADMIN","a");ownerId=account("PROPIETARIO","o");vetId=account("VETERINARIO","v");admin=login("a");owner=login("o");vet=login("v");}
 long role(String name){return db.queryForObject("select id_rol from roles where nombre=?",Long.class,name);}
 String email(String suffix){return suffix+tag+"@example.invalid";}
 long account(String r,String suffix){return db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Persona','Sintética','DNI',?,?,?,?,current_timestamp) returning id_usuario",Long.class,"00"+tag+suffix,email(suffix),encoder.encode(password),role(r));}
 String login(String suffix)throws Exception{var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",email(suffix),"contrasena",password)))).andExpect(status().isOk()).andReturn();return "Bearer "+json.readTree(result.getResponse().getContentAsString()).get("token").asString();}
 Map<String,Object> body(String suffix,long rol){return new HashMap<>(Map.of("nombres","Nombre","apellidos","Real sintético","tipoDocumento","DNI","nroDocumento","00"+tag+suffix,"correo",email(suffix),"nroCelular","999111222","contrasena",password,"idRol",rol));}
 @Test void detalleRolesFotosYListadosSonExclusivosDeAdmin()throws Exception{
  for(String t:List.of(owner,vet))for(String path:List.of("/api/usuarios","/api/usuarios/roles","/api/usuarios/"+adminId,"/api/usuarios/"+adminId+"/foto"))mvc.perform(get(path).header("Authorization",t)).andExpect(status().isForbidden());
  mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/usuarios/roles").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
  mvc.perform(get("/api/usuarios/{id}",ownerId).header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.nroDocumento").value("00"+tag+"o")).andExpect(jsonPath("$.contrasena").doesNotExist()).andExpect(jsonPath("$.fotoDisponible").value(false));
  mvc.perform(get("/api/usuarios/{id}",Long.MAX_VALUE).header("Authorization",admin)).andExpect(status().isNotFound());
 }
 @Test void crearAdminExigeConfirmacionYRolesInferioresNoEscalan()throws Exception{
  var p=body("new-admin",roleAdmin);mvc.perform(post("/api/usuarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isBadRequest());
  p.put("confirmarAdmin",true);for(String t:List.of(owner,vet))mvc.perform(post("/api/usuarios").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isForbidden());
  mvc.perform(post("/api/usuarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("ADMIN")).andExpect(jsonPath("$.contrasena").doesNotExist());
  assertTrue(encoder.matches(password,db.queryForObject("select contrasena from usuario where correo=?",String.class,email("new-admin"))));
 }
 @Test void duplicadosYEdicionMantienenRolHashYPerfiles()throws Exception{
  long perfil=db.queryForObject("insert into veterinario(id_usuario,especialidad,nro_colegiatura) values (?,'General',?) returning id_veterinario",Long.class,vetId,tag);
  var p=body("v",role("VETERINARIO"));p.remove("contrasena");String hash=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,vetId);
  mvc.perform(put("/api/usuarios/{id}",vetId).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isOk());
  p.put("idRol",roleAdmin);mvc.perform(put("/api/usuarios/{id}",vetId).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isBadRequest());
  p.remove("idRol");p.put("correo",email("o"));mvc.perform(put("/api/usuarios/{id}",vetId).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isBadRequest());
  assertEquals(hash,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,vetId));assertEquals(vetId,db.queryForObject("select id_usuario from veterinario where id_veterinario=?",Long.class,perfil));
  var fresh=body("dup",roleOwner);fresh.put("nroDocumento","00"+tag+"o");mvc.perform(post("/api/usuarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fresh))).andExpect(status().isBadRequest());
 }
 @Test void autoeliminacionYPerfilesAsociadosSeConservan()throws Exception{
  mvc.perform(delete("/api/usuarios/{id}",adminId).header("Authorization",admin)).andExpect(status().isBadRequest());
  db.update("insert into propietario(id_usuario,direccion,fecha_registro_clinica,estado_cliente) values (?,'Dirección registrada',current_date,'ACTIVO')",ownerId);
  mvc.perform(get("/api/usuarios/{id}",ownerId).header("Authorization",admin)).andExpect(jsonPath("$.direccionContacto").value("Dirección registrada"));
  mvc.perform(delete("/api/usuarios/{id}",ownerId).header("Authorization",admin)).andExpect(status().isConflict());
  mvc.perform(delete("/api/usuarios/{id}",ownerId).header("Authorization",vet)).andExpect(status().isForbidden());assertEquals(1,db.queryForObject("select count(*) from usuario where id_usuario=?",Integer.class,ownerId));
 }
 @Test void cambiarCorreoInvalidaSesionAnteriorYConservaCredencial()throws Exception {
  var edit=body("o",roleOwner);edit.remove("contrasena");edit.put("correo",email("correo-nuevo"));
  mvc.perform(put("/api/usuarios/{id}",ownerId).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit))).andExpect(status().isOk());
  mvc.perform(get("/api/usuarios/me").header("Authorization",owner)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",email("correo-nuevo"),"contrasena",password)))).andExpect(status().isOk());
 }
 @Test void rolVigenteSeCompruebaEnJwtAnteCambioExterno()throws Exception {
  db.update("update usuario set id_rol=? where id_usuario=?",roleOwner,adminId);
  mvc.perform(get("/api/usuarios").header("Authorization",admin)).andExpect(status().isUnauthorized());
 }
 @Test void eliminacionesSimultaneasNoDejanElSistemaSinAdmin()throws Exception {
  long second=account("ADMIN","second");String secondToken=login("second");
  db.update("update usuario set id_rol=? where id_rol=? and id_usuario not in (?,?)",roleOwner,roleAdmin,adminId,second);
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var go=new java.util.concurrent.CountDownLatch(1);
  try{
   var a=pool.submit(()->{go.await();return mvc.perform(delete("/api/usuarios/{id}",second).header("Authorization",admin)).andReturn().getResponse().getStatus();});
   var b=pool.submit(()->{go.await();return mvc.perform(delete("/api/usuarios/{id}",adminId).header("Authorization",secondToken)).andReturn().getResponse().getStatus();});
   go.countDown();var statuses=List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals(1,statuses.stream().filter(status->status==204).count());assertTrue(statuses.stream().allMatch(status->List.of(204,409,401).contains(status)));
   assertEquals(1L,db.queryForObject("select count(*) from usuario where id_rol=?",Long.class,roleAdmin));
  }finally{pool.shutdownNow();}
 }
 @Test void ultimoAdminTieneProteccionEnServicioInclusoSinIdentidadJwt()throws Exception{
  db.update("update usuario set id_rol=? where id_rol=? and id_usuario<>?",roleOwner,roleAdmin,adminId);
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("prueba-servicio","",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));org.springframework.security.core.context.SecurityContextHolder.setContext(context);
  try{var e=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->usuarios.eliminar(adminId));assertEquals(409,e.getStatusCode().value());}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
  assertEquals(1,db.queryForObject("select count(*) from usuario where id_usuario=?",Integer.class,adminId));
 }
 @Test void catalogoRealPermiteCrearPropietarioYDuplicadosNoDejanCuentasParciales()throws Exception{
  mvc.perform(get("/api/usuarios/roles")).andExpect(status().isUnauthorized());
  var catalogo=mvc.perform(get("/api/usuarios/roles").header("Authorization",admin)).andExpect(status().isOk()).andReturn();
  var roles=json.readTree(catalogo.getResponse().getContentAsString());
  for(var r:roles)assertEquals(role(r.get("nombre").asString()),r.get("idRol").asLong());
  var p=body("cuenta",roleOwner);p.put("nroDocumento","0123"+tag);
  long before=db.queryForObject("select count(*) from usuario",Long.class);
  var created=mvc.perform(post("/api/usuarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p))).andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("PROPIETARIO")).andExpect(jsonPath("$.contrasena").doesNotExist()).andReturn();
  long id=json.readTree(created.getResponse().getContentAsString()).get("idUsuario").asLong();
  mvc.perform(get("/api/usuarios/{id}",id).header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.nroDocumento").value("0123"+tag));
  var duplicate=body("otro",roleOwner);duplicate.put("correo",email("cuenta"));
  mvc.perform(post("/api/usuarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(duplicate))).andExpect(status().isBadRequest());
  duplicate.put("correo",email("otro"));duplicate.put("nroDocumento","0123"+tag);
  mvc.perform(post("/api/usuarios").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(duplicate))).andExpect(status().isBadRequest());
  assertEquals(before+1,db.queryForObject("select count(*) from usuario",Long.class));
  assertEquals(0,db.queryForObject("select count(*) from usuario where correo=?",Integer.class,email("otro")));
 }
 @Test void eliminarUsuarioConPerfilVeterinarioSeRechazaPorRestYSinBorradoEnCascada()throws Exception{
  long perfil=db.queryForObject("insert into veterinario(id_usuario,especialidad,nro_colegiatura) values (?,'General',?) returning id_veterinario",Long.class,vetId,"seg-"+tag);
  mvc.perform(delete("/api/usuarios/{id}",vetId).header("Authorization",owner)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/usuarios/{id}",vetId).header("Authorization",admin)).andExpect(status().isConflict());
  assertEquals(1,db.queryForObject("select count(*) from usuario where id_usuario=?",Integer.class,vetId));
  assertEquals(1,db.queryForObject("select count(*) from veterinario where id_veterinario=? and id_usuario=?",Integer.class,perfil,vetId));
 }

}
