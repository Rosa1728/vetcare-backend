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
class CambiarContrasenaHttpIT extends PostgresIntegrationSupport {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder; @Autowired ObjectMapper json; @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
 String tag,password,next;long id,other;String token;
 @BeforeEach void fixture() throws Exception {assertEquals("vetcare_p0_integration",db.queryForObject("select current_database()",String.class));tag=UUID.randomUUID().toString().substring(0,8);password="Qa!"+tag;next="Nb!"+UUID.randomUUID();id=user("ADMIN","main");other=user("PROPIETARIO","other");token=login("main",password);}
 long user(String role,String suffix){return db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Persona','Sintética','DNI',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,tag+suffix,email(suffix),encoder.encode(password),role);}
 String email(String suffix){return suffix+tag+"@example.invalid";}
 String login(String suffix,String pass)throws Exception {var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",email(suffix),"contrasena",pass)))).andExpect(status().isOk()).andReturn();return "Bearer "+json.readTree(result.getResponse().getContentAsString()).get("token").asString();}
 org.springframework.test.web.servlet.ResultActions change(String auth,String current,String fresh,String confirmation)throws Exception {return mvc.perform(post("/api/usuarios/me/contrasena").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("contrasenaActual",current,"nuevaContrasena",fresh,"confirmarContrasena",confirmation))));}
 @Test void cambioParaTresRolesInvalidaTodosLosTokensAnterioresYConservaIdentidad()throws Exception {
  for(String role:List.of("ADMIN","VETERINARIO","PROPIETARIO")){
   String suffix=role.toLowerCase();long account=user(role,suffix);String a=login(suffix,password),b=login(suffix,password);var before=db.queryForMap("select nombres,apellidos,correo,id_rol from usuario where id_usuario=?",account);
   change(a,password,next,next).andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").exists()).andExpect(jsonPath("$.contrasena").doesNotExist()).andExpect(jsonPath("$.hash").doesNotExist());
   String stored=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,account);assertTrue(stored.startsWith("$2"));assertTrue(encoder.matches(next,stored));assertFalse(stored.equals(next));assertEquals(before,db.queryForMap("select nombres,apellidos,correo,id_rol from usuario where id_usuario=?",account));
   for(String old:List.of(a,b))mvc.perform(get("/api/usuarios/me").header("Authorization",old)).andExpect(status().isUnauthorized());
   mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",email(suffix),"contrasena",password)))).andExpect(status().isUnauthorized());
   String fresh=login(suffix,next);mvc.perform(get("/api/usuarios/me").header("Authorization",fresh)).andExpect(status().isOk());
  }
 }
 @Test void erroresDeCamposNoCambianHashNiInvalidanToken()throws Exception {
  String before=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id);
  change(token,"Incorrecta!9",next,next).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.contrasenaActual").exists());
  change(token,password,"debil","debil").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.nuevaContrasena").exists());
  change(token,password,next,"Otra!1234").andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.confirmarContrasena").exists());
  change(token,password,password,password).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.nuevaContrasena").exists());
  assertEquals(before,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id));mvc.perform(get("/api/usuarios/me").header("Authorization",token)).andExpect(status().isOk());
 }
 @Test void anonimoYTokenAlteradoNoCambianCredenciales()throws Exception {
  String body=json.writeValueAsString(Map.of("contrasenaActual",password,"nuevaContrasena",next,"confirmarContrasena",next));mvc.perform(post("/api/usuarios/me/contrasena").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());mvc.perform(post("/api/usuarios/me/contrasena").header("Authorization","Bearer invalido").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());assertTrue(encoder.matches(password,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id)));
 }
 @Test void idEnviadoPorClienteNuncaSeleccionaOtraCuenta()throws Exception {
  var body=new HashMap<String,Object>(Map.of("contrasenaActual",password,"nuevaContrasena",next,"confirmarContrasena",next,"idUsuario",other));
  var r=mvc.perform(post("/api/usuarios/me/contrasena").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();assertTrue(r.getResponse().getStatus()==200||r.getResponse().getStatus()==400);assertTrue(encoder.matches(password,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,other)));
 }
 @Test void cincoIntentosIncorrectosLimitanLaOperacion()throws Exception {for(int i=0;i<5;i++)change(token,"Incorrecta!9",next,next).andExpect(status().isBadRequest());change(token,password,next,next).andExpect(status().isTooManyRequests());assertTrue(encoder.matches(password,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id)));}
 @Test void contrasenaActualExcesivaSeRechazaSinErrorInterno()throws Exception {
  change(token,"á".repeat(80),next,next).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.contrasenaActual").exists());
  assertTrue(encoder.matches(password,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id)));
 }
 @Test void tokenSinHuellaSeRechaza()throws Exception {
  var now=java.time.Instant.now();
  var oldClaims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("vetcare").subject(email("main")).issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("idUsuario",id).claim("rol","ADMIN").build();
  var header=org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
  String legacy=jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header,oldClaims)).getTokenValue();
  mvc.perform(get("/api/usuarios/me").header("Authorization","Bearer "+legacy)).andExpect(status().isUnauthorized());
  var part=token.substring(7).split("\\.");var claims=json.readTree(java.util.Base64.getUrlDecoder().decode(part[1]));assertTrue(claims.has("credencial"));assertFalse(claims.toString().contains(db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id)));
 }
}
