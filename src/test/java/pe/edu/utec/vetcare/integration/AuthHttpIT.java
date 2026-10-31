package pe.edu.utec.vetcare.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import pe.edu.utec.vetcare.service.RecuperacionContrasenaService;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(AuthHttpIT.Config.class)
@TestPropertySource(properties={"vetcare.recuperacion.habilitada=true","vetcare.recuperacion.correo-habilitado=true","vetcare.recuperacion.url-publica=http://localhost:5174"})
class AuthHttpIT extends PostgresIntegrationSupport {
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2030-01-10T15:00:00Z");
  public ZoneId getZone(){return ZoneId.of("America/Lima");}public Clock withZone(ZoneId z){return Clock.fixed(now,z);}public Instant instant(){return now;}
 }
 /** SMTP local real: nunca utiliza Internet, credenciales ni servicios personales. */
 static class LocalSmtp implements AutoCloseable {
  final ServerSocket socket;final BlockingQueue<String> messages=new LinkedBlockingQueue<>();volatile boolean fail;final Thread thread;
  LocalSmtp()throws IOException{socket=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1"));thread=new Thread(this::serve,"smtp-aislado");thread.setDaemon(true);thread.start();}
  void serve(){while(!socket.isClosed())try(Socket client=socket.accept()){
   client.setSoTimeout(4000);var input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));var output=new PrintWriter(new OutputStreamWriter(client.getOutputStream(),StandardCharsets.US_ASCII),true);
   output.print("220 localhost SMTP test\r\n");output.flush();String line;boolean data=false;StringBuilder body=new StringBuilder();
   while((line=input.readLine())!=null){
    if(data){if(line.equals(".")){messages.add(body.toString());output.print("250 accepted\r\n");data=false;}else body.append(line.startsWith("..")?line.substring(1):line).append("\r\n");}
    else if(line.startsWith("DATA")){if(fail)output.print("451 simulated temporary failure\r\n");else{data=true;output.print("354 send data\r\n");}}
    else if(line.startsWith("QUIT")){output.print("221 bye\r\n");output.flush();break;}
    else output.print("250 OK\r\n");output.flush();
   }
  }catch(IOException ignored){/* Sin logs de contenido SMTP ni enlaces. */}}
  public void close()throws IOException{socket.close();}
 }
 @TestConfiguration static class Config {
  @Bean @Primary MutableClock authClock(){return new MutableClock();}
  @Bean(destroyMethod="close") LocalSmtp smtp()throws IOException{return new LocalSmtp();}
  @Bean @Primary JavaMailSender sender(LocalSmtp smtp){var sender=new JavaMailSenderImpl();sender.setHost("127.0.0.1");sender.setPort(smtp.socket.getLocalPort());sender.setDefaultEncoding("UTF-8");var p=sender.getJavaMailProperties();p.setProperty("mail.smtp.connectiontimeout","3000");p.setProperty("mail.smtp.timeout","3000");p.setProperty("mail.smtp.writetimeout","3000");return sender;}
 }
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired PasswordEncoder passwords;@Autowired RecuperacionContrasenaService recovery;@Autowired LocalSmtp smtp;@Autowired MutableClock clock;
 String key,password,email;long user;
 @BeforeEach void fixture(){
  assertEquals("vetcare_p0_integration",db.queryForObject("select current_database()",String.class));db.update("delete from recuperacion_contrasena");smtp.messages.clear();smtp.fail=false;clock.now=Instant.parse("2030-01-10T15:00:00Z");
  key=UUID.randomUUID().toString().substring(0,10);password="Qa!"+UUID.randomUUID();email="auth-"+key+"@example.invalid";user=createUser("PROPIETARIO",email,password);
 }
 @Test void loginDeTresRolesRechazaClaveIncorrectaYProtegeJwt()throws Exception{
  for(String role:List.of("ADMIN","VETERINARIO","PROPIETARIO")){
   String address=role.toLowerCase()+"-"+key+"@example.invalid";createUser(role,address,password);String token=login(address,password);
   mvc.perform(get("/api/usuarios/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value(role));
  }
  postJson("/api/auth/login",Map.of("correo",email,"contrasena","Incorrecta1!")).andExpect(status().isUnauthorized());mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/usuarios/me").header("Authorization","Bearer token-inventado")).andExpect(status().isUnauthorized());
 }
 @Test void registroPublicoPersistePropietarioYFijaRolSinExponerHash()throws Exception{
  var body=registration();body.put("idRol",1);body.put("rol","ADMIN");
  var r=postJson("/api/auth/registro",body).andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("PROPIETARIO")).andExpect(jsonPath("$.contrasena").doesNotExist()).andReturn();
  long id=json.readTree(r.getResponse().getContentAsByteArray()).get("idUsuario").asLong();
  String hash=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id);assertTrue(passwords.matches(password,hash));assertNotEquals(password,hash);
  assertEquals(1,db.queryForObject("select count(*) from propietario where id_usuario=? and direccion=?",Integer.class,id,"Dirección sintética áéíóúñ"));
  String token=login(body.get("correo").toString(),password);mvc.perform(get("/api/usuarios/me").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.rol").value("PROPIETARIO"));
 }
 // Preparada para PostgreSQL temporal; no forma parte de mvn test.
 @Test void direccionOpcionalEnRegistroSePuedeGuardarLuegoDesdeMiPerfil()throws Exception{
  for(String mode:List.of("ausente","null","vacia","informada")){
   var body=registration();
   if(mode.equals("ausente"))body.remove("direccion");
   else if(mode.equals("null"))body.put("direccion",null);
   else if(mode.equals("vacia"))body.put("direccion","");
   String expected=(String)body.get("direccion");
   var response=postJson("/api/auth/registro",body).andExpect(status().isCreated()).andReturn();
   long id=json.readTree(response.getResponse().getContentAsByteArray()).get("idUsuario").asLong();
   assertEquals(expected,db.queryForObject("select direccion from propietario where id_usuario=?",String.class,id));
   String hash=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id);
   String authorization="Bearer "+login(body.get("correo").toString(),password);
   String address="Dirección posterior 456";
   mvc.perform(patch("/api/propietarios/me").header("Authorization",authorization)
    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("direccion",address)))).andExpect(status().isOk());
   mvc.perform(get("/api/propietarios/me").header("Authorization",authorization)).andExpect(status().isOk())
    .andExpect(jsonPath("$.direccion").value(address));
   assertEquals(address,db.queryForObject("select direccion from propietario where id_usuario=?",String.class,id));
   assertEquals(hash,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,id));
  }
 }
 @Test void registroValidaDuplicadosDocumentosYPoliticaCompartida()throws Exception{
  var body=registration();postJson("/api/auth/registro",body).andExpect(status().isCreated());postJson("/api/auth/registro",body).andExpect(status().isBadRequest());
  body.put("correo","duplicate-"+key+"@example.invalid");postJson("/api/auth/registro",body).andExpect(status().isBadRequest());
  for(String weak:List.of("Corta1!","solominuscula1!","SOLOMAYUSCULA1!","SinNumero!","SinSimbolo123","Password1!","VetCare2026!","Aa1!"+"é".repeat(35))){var invalid=registration();invalid.put("contrasena",weak);postJson("/api/auth/registro",invalid).andExpect(status().isBadRequest());}
  var invalid=registration();invalid.put("nroDocumento","123");postJson("/api/auth/registro",invalid).andExpect(status().isBadRequest());invalid=registration();invalid.put("nroCelular","abc");postJson("/api/auth/registro",invalid).andExpect(status().isBadRequest());
 }
 @Test void solicitudNoEnumeraCuentasYCorreoPasaPorSmtpLocalReal()throws Exception{
  var known=request(email).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
  var unknown=request("unknown-"+key+"@example.invalid").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();assertEquals(known,unknown);
  recovery.procesarPendientes();String token=mailToken();assertEquals(0,smtp.messages.size());
  String stored=db.queryForObject("select token_hash from recuperacion_contrasena where correo=?",String.class,email);assertNotEquals(token,stored);assertEquals(64,stored.length());
  assertEquals(2,db.queryForObject("select count(*) from recuperacion_contrasena where estado='PROCESADA'",Integer.class));
  request(email).andExpect(status().isTooManyRequests());
 }
 @Test void restableceUnaVezConBcryptYRechazaClaveAnterior()throws Exception{
  request(email).andExpect(status().isAccepted());recovery.procesarPendientes();String token=mailToken(),next="Nueva!"+UUID.randomUUID();
  reset(token,next).andExpect(status().isOk());reset(token,next).andExpect(status().isBadRequest());login(email,next);
  postJson("/api/auth/login",Map.of("correo",email,"contrasena",password)).andExpect(status().isUnauthorized());
  assertTrue(passwords.matches(next,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,user)));
 }
 @Test void rechazaTokenInvalidoVencidoYClaveInseguraSinCambiarCuenta()throws Exception{
  String original=db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,user);
  reset("x".repeat(43),password).andExpect(status().isBadRequest());request(email).andExpect(status().isAccepted());recovery.procesarPendientes();String token=mailToken();
  reset(token,"Password1!").andExpect(status().isBadRequest());clock.now=clock.now.plus(Duration.ofMinutes(31));reset(token,password).andExpect(status().isBadRequest());
  assertEquals(original,db.queryForObject("select contrasena from usuario where id_usuario=?",String.class,user));
 }
 @Test void dosUsosSimultaneosSoloRestablecenUnaVez()throws Exception{
  request(email).andExpect(status().isAccepted());recovery.procesarPendientes();String token=mailToken();var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
  try{Callable<Integer> task=()->{barrier.await(5,TimeUnit.SECONDS);return reset(token,"Nueva!"+UUID.randomUUID()).andReturn().getResponse().getStatus();};var a=pool.submit(task);var b=pool.submit(task);var results=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));Collections.sort(results);assertEquals(List.of(200,400),results);}finally{pool.shutdownNow();}
 }
 @Test void fallosDeCorreoNoSeMarcanEnviadosYSeReintentanSinTokenValido()throws Exception{
  request(email).andExpect(status().isAccepted());smtp.fail=true;recovery.procesarPendientes();assertEquals(0,smtp.messages.size());
  assertEquals("PENDIENTE",db.queryForObject("select estado from recuperacion_contrasena where correo=?",String.class,email));assertNull(db.queryForObject("select token_hash from recuperacion_contrasena where correo=?",String.class,email));
  smtp.fail=false;recovery.procesarPendientes();String token=mailToken();reset(token,password).andExpect(status().isOk());
 }
 @Test void configuracionInactivaRespondeErrorRealSinGuardarSolicitud()throws Exception{
  ReflectionTestUtils.setField(recovery,"correoActivo",false);try{request(email).andExpect(status().isServiceUnavailable());request("unknown-"+key+"@example.invalid").andExpect(status().isServiceUnavailable());assertEquals(0,db.queryForObject("select count(*) from recuperacion_contrasena",Integer.class));}finally{ReflectionTestUtils.setField(recovery,"correoActivo",true);}
 }
 private String mailToken()throws Exception{String raw=smtp.messages.poll(5,TimeUnit.SECONDS);assertNotNull(raw,"El SMTP local debe recibir el mensaje");var mime=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));var matcher=Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(mime.getContent().toString());assertTrue(matcher.find(),"El correo debe contener un enlace de recuperación");return matcher.group(1);}
 private ResultActions request(String address)throws Exception{return postJson("/api/auth/recuperar-contrasena",Map.of("correo",address));}
 private ResultActions reset(String token,String pw)throws Exception{return postJson("/api/auth/restablecer-contrasena",Map.of("token",token,"contrasena",pw));}
 private ResultActions postJson(String path,Object body)throws Exception{return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
 private String login(String address,String pw)throws Exception{var r=postJson("/api/auth/login",Map.of("correo",address,"contrasena",pw)).andExpect(status().isOk()).andReturn();return json.readTree(r.getResponse().getContentAsByteArray()).get("token").asText();}
 private long createUser(String role,String address,String pw){return db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Cuenta','Auth sintética','PRUEBA',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,UUID.randomUUID().toString().substring(0,18),address,passwords.encode(pw),role);}
 private Map<String,Object> registration(){return new HashMap<>(Map.of("nombres","Persona","apellidos","Sintética","tipoDocumento","DNI","nroDocumento",String.format("%08d",ThreadLocalRandom.current().nextInt(100000000)),"correo","register-"+UUID.randomUUID()+"@example.invalid","contrasena",password,"nroCelular","999888777","direccion","Dirección sintética áéíóúñ"));}
}
