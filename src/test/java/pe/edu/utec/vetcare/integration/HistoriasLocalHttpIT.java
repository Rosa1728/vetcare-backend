package pe.edu.utec.vetcare.integration;
import java.nio.file.Path;
import java.util.*;
import java.security.SecureRandom;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.location=classpath:/application-integration.properties","spring.datasource.url=jdbc:postgresql://localhost:5432/vetcare_pruebas_local","spring.datasource.username=rosapp","spring.datasource.password=","spring.flyway.enabled=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE,printOnlyOnFailure=false)
@Transactional
@EnabledIfSystemProperty(named="vetcare.local.historias",matches="true")
class HistoriasLocalHttpIT {
 @TempDir static Path photos;
 private static final String KEY=key();private static String key(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getEncoder().encodeToString(b);}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("JWT_SECRET",()->KEY);r.add("VETCARE_HISTORIAS_DIR",()->photos.toString());}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;
 String tag,password,owner,foreign;long pet,foreignPet;
 @BeforeEach void fixture()throws Exception{
  assertEquals("vetcare_pruebas_local",db.queryForObject("select current_database()",String.class));assertTrue(db.queryForObject("select inet_server_addr() in (inet '::1',inet '127.0.0.1') and inet_server_port()=5432",Boolean.class));
  tag="HQA"+UUID.randomUUID().toString().substring(0,8);password=UUID.randomUUID().toString();pet=pet(account("a"+tag));foreignPet=pet(account("b"+tag));owner=login("a"+tag);foreign=login("b"+tag);
 }
 @Test void paginacionBusquedaCategoriaYDetallePublicoSonRealesYAcotados()throws Exception{
  for(int i=0;i<43;i++)db.update("insert into historia_publicacion(id_mascota,titulo,categoria,contenido,fecha_creacion) values (?,?,?,'Descripción pública QA',current_timestamp)",pet,tag+" Historia "+i,i%2==0?"Celebración":"Otros");
  var response=mvc.perform(get("/api/publicaciones/pagina").param("q",tag).param("size","9").param("page","4")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(43)).andExpect(jsonPath("$.totalPages").value(5)).andExpect(jsonPath("$.content.length()").value(7)).andReturn();
  long id=json.readTree(response.getResponse().getContentAsByteArray()).get("content").get(0).get("id").asLong();
  mvc.perform(get("/api/publicaciones/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.contenido").value("Descripción pública QA")).andExpect(jsonPath("$.correo").doesNotExist()).andExpect(jsonPath("$.fotoArchivo").doesNotExist());
  mvc.perform(get("/api/publicaciones/pagina").param("q",tag).param("categoria","Celebración").param("size","3")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(22)).andExpect(jsonPath("$.content.length()").value(3));
  mvc.perform(get("/api/publicaciones/pagina").param("q",tag+"%" )).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/publicaciones/pagina").param("size","40")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/publicaciones/mis")).andExpect(status().isUnauthorized());
 }
 @Test void propietarioCreaSinFotoYNoPuedeModificarHistoriasAjenas()throws Exception{
  long id=create(pet,owner);
  mvc.perform(get("/api/publicaciones/mis/{id}",id).header("Authorization",owner)).andExpect(status().isOk());
  mvc.perform(get("/api/publicaciones/mis/{id}",id).header("Authorization",foreign)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/publicaciones/{id}",id).header("Authorization",foreign)).andExpect(status().isForbidden());
  mvc.perform(post("/api/publicaciones").header("Authorization",foreign).contentType("application/json").content(json.writeValueAsString(body(pet)))).andExpect(status().isForbidden());
  assertEquals(1,db.queryForObject("select count(*) from historia_publicacion where id_publicacion=?",Integer.class,id));
 }
 @Test void fotografiaSeGuardaEnDirectorioTemporalYSeLeePublicamente()throws Exception{
  long id=create(pet,owner);var image=new java.awt.image.BufferedImage(512,512,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);
  var photo=new MockMultipartFile("foto","historia.png","image/png",out.toByteArray());
  mvc.perform(multipart("/api/publicaciones/{id}/foto",id).file(photo).header("Authorization",foreign)).andExpect(status().isForbidden());
  mvc.perform(multipart("/api/publicaciones/{id}/foto",id).file(photo).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.foto").value(true));
  mvc.perform(get("/api/publicaciones/{id}/foto",id)).andExpect(status().isOk()).andExpect(content().bytes(out.toByteArray()));
  mvc.perform(multipart("/api/publicaciones/{id}/foto",id).file(new MockMultipartFile("foto","falsa.png","image/png",new byte[]{1,2,3})).header("Authorization",owner)).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/publicaciones/{id}",id).header("Authorization",owner)).andExpect(status().isNoContent());
  mvc.perform(get("/api/publicaciones/{id}",id)).andExpect(status().isNotFound());
 }
 private Map<String,Object> body(long p){return Map.of("idMascota",p,"titulo",tag+" Historia","categoria","Celebración","contenido","Primer párrafo QA.\n\nSegundo párrafo QA.");}
 private long create(long p,String token)throws Exception{var r=mvc.perform(post("/api/publicaciones").header("Authorization",token).contentType("application/json").content(json.writeValueAsString(body(p)))).andExpect(status().isCreated()).andExpect(jsonPath("$.foto").value(false)).andReturn();return json.readTree(r.getResponse().getContentAsByteArray()).get("id").asLong();}
 private long account(String name){return db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('QA','Comunidad','PRUEBA',?,?,?,(select id_rol from roles where nombre='PROPIETARIO'),current_timestamp) returning id_usuario",Long.class,name,name+"@example.invalid",encoder.encode(password));}
 private long pet(long user){long ownerId=db.queryForObject("insert into propietario(id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,user);return db.queryForObject("insert into mascota(id_propietario,nombre,especie) values (?,'Mascota QA','Gato') returning id_mascota",Long.class,ownerId);}
 private String login(String name)throws Exception{var r=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("correo",name+"@example.invalid","contrasena",password)))).andExpect(status().isOk()).andReturn();return "Bearer "+json.readTree(r.getResponse().getContentAsByteArray()).get("token").asText();}
}
