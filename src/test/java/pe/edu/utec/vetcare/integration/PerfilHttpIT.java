package pe.edu.utec.vetcare.integration;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PerfilHttpIT extends PostgresIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    static final Path PHOTOS = tempPhotos();
    String secret, suffix, owner, vet, admin, other;
    long ownerId, vetId, strangerId;
    @DynamicPropertySource
    static void photos(DynamicPropertyRegistry registry) { registry.add("vetcare.perfiles.directorio", PHOTOS::toString); }
    private static Path tempPhotos() { try { return Files.createTempDirectory("vetcare-profile-it-"); } catch (Exception e) { throw new IllegalStateException(e); } }
    @BeforeEach void fixture() throws Exception {
        assertEquals("vetcare_p0_integration", jdbc.queryForObject("select current_database()", String.class));
        secret=UUID.randomUUID().toString(); suffix=UUID.randomUUID().toString().substring(0,8);
        ownerId=user("PROPIETARIO","owner","DNI"); strangerId=user("PROPIETARIO","other","CE");
        for (long id : new long[]{ownerId,strangerId}) jdbc.update("insert into propietario(id_usuario,estado_cliente,fecha_registro_clinica,direccion) values (?,'ACTIVO',current_date,'Dirección inicial')",id);
        vetId=user("VETERINARIO","vet","CE"); user("ADMIN","admin","DNI");
        jdbc.update("insert into veterinario(id_usuario,especialidad,nro_colegiatura) values (?,'Cirugía',?)",vetId,"IT-"+suffix);
        owner=login("owner"); vet=login("vet"); admin=login("admin"); other=login("other");
    }
    @Test void propietarioGuardaSoloDatosPropiosYConservaIdentidadCredenciales() throws Exception {
        String hash=jdbc.queryForObject("select contrasena from usuario where id_usuario=?",String.class,ownerId);
        mvc.perform(patch("/api/propietarios/me").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("nroCelular","999888777","direccion","Av. Perú 125"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombres").value("Prueba")).andExpect(jsonPath("$.contrasena").doesNotExist());
        String relogin=login("owner");
        mvc.perform(get("/api/usuarios/me").header("Authorization",relogin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.apellidos").value("Perfil")).andExpect(jsonPath("$.nroCelular").value("999888777"))
            .andExpect(jsonPath("$.tipoDocumento").value("DNI")).andExpect(jsonPath("$.rol").value("PROPIETARIO"));
        mvc.perform(get("/api/propietarios/me").header("Authorization",relogin)).andExpect(status().isOk()).andExpect(jsonPath("$.direccion").value("Av. Perú 125"));
        assertEquals(hash,jdbc.queryForObject("select contrasena from usuario where id_usuario=?",String.class,ownerId));
        assertEquals("Prueba",jdbc.queryForObject("select nombres from usuario where id_usuario=?",String.class,strangerId));
    }
    @Test void veterinarioSoloEditaContactoYConservaTratamientoAsignadoPorAdmin() throws Exception {
        long id=jdbc.queryForObject("select id_veterinario from veterinario where id_usuario=?",Long.class,vetId);
        mvc.perform(put("/api/veterinarios/{id}",id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("especialidad","Cirugía","nroColegiatura","IT-"+suffix,"tratamientoProfesional","Dra."))))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/veterinarios/me").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nroCelular\":\"987222333\",\"direccion\":\"Dirección sintética\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tratamientoProfesional").value("Dra."));
        mvc.perform(get("/api/veterinarios/me").header("Authorization",login("vet"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.nombres").value("Prueba")).andExpect(jsonPath("$.especialidad").value("Cirugía"));
        for(String field:new String[]{"nombres","apellidos","tratamientoProfesional"})
            mvc.perform(patch("/api/veterinarios/me").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(field,"Otro")))).andExpect(status().isBadRequest());
        assertEquals("Dra.",jdbc.queryForObject("select tratamiento_profesional from veterinario where id_usuario=?",String.class,vetId));
        assertEquals(1,jdbc.queryForObject("select count(*) from flyway_schema_history where version='11' and success",Integer.class));
    }
    @Test void rechazaSuplantacionCamposProtegidosYRolesIncorrectos() throws Exception {
        for(String field:new String[]{"idUsuario","idPropietario","rol","correo","tipoDocumento","nroDocumento","contrasena","fotoPerfil"}) {
            mvc.perform(patch("/api/propietarios/me").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(field,"otro")))).andExpect(status().isBadRequest());
        }
        for(String field:new String[]{"especialidad","nroColegiatura"}) mvc.perform(patch("/api/veterinarios/me").header("Authorization",vet)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(field,"otro")))).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/propietarios/me").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content("{\"nombres\":\"Otro\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/veterinarios/me").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"nombres\":\"Otro\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/propietarios/me").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"nombres\":\"Otro\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/{id}",strangerId).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
    }
    @Test void validacionAtomicaYDocumentosSeparados() throws Exception {
        for(String body:new String[]{"{}","{\"nombres\":\" \"}","{\"nroCelular\":\"abc\"}","{\"nombres\":null}","{\"nombres\":\"Cambio\",\"direccion\":\""+"x".repeat(201)+"\"}"})
            mvc.perform(patch("/api/propietarios/me").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/veterinarios/me").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content("{\"tratamientoProfesional\":\"Doctor\"}")).andExpect(status().isBadRequest());
        assertEquals("Prueba",jdbc.queryForObject("select nombres from usuario where id_usuario=?",String.class,ownerId));
        mvc.perform(get("/api/usuarios/me").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.tipoDocumento").value("CE"))
            .andExpect(jsonPath("$.nroDocumento").value("CE"+suffix+"other"));
    }
    @Test void fotografiaPersisteTrasLoginSeReemplazaYSeQuitaSinTocarOtraCuenta() throws Exception {
        byte[] image=image();
        mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("foto","avatar.png","image/png",image)).header("Authorization",owner)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/me/foto").header("Authorization",login("owner"))).andExpect(status().isOk()).andExpect(content().bytes(image));
        mvc.perform(get("/api/usuarios/me/foto").header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("foto","avatar.png","image/png",image)).header("Authorization",owner)).andExpect(status().isOk());
        mvc.perform(delete("/api/usuarios/me/foto").header("Authorization",owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/me/foto").header("Authorization",login("owner"))).andExpect(status().isNotFound());
        assertNull(jdbc.queryForObject("select foto_perfil from usuario where id_usuario=?",String.class,ownerId));
    }
    @Test void fotografiaRechazaFalsosExcesoYAnonimos() throws Exception {
        for(byte[] invalid:new byte[][]{"imagen falsa".getBytes(),new byte[2*1024*1024+1]})
            mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("foto","avatar.png","image/png",invalid)).header("Authorization",owner)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/usuarios/me/foto").file(new MockMultipartFile("foto","avatar.svg","image/svg+xml","<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes())).header("Authorization",owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/usuarios/me/foto")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/usuarios/me/foto")).andExpect(status().isUnauthorized());
    }
    @Test void administradorConservaIdentidadPropiaYRechazaDuplicadosAjenos() throws Exception {
        var current=jdbc.queryForMap("select * from usuario where id_usuario=?",ownerId);
        var payload=new java.util.HashMap<String,Object>();
        payload.put("nombres","Prueba"); payload.put("apellidos","Perfil"); payload.put("tipoDocumento",current.get("tipo_documento"));
        payload.put("nroDocumento",current.get("nro_documento")); payload.put("correo",current.get("correo")); payload.put("nroCelular","999111222");
        mvc.perform(put("/api/usuarios/{id}",ownerId).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isOk());
        payload.put("correo",jdbc.queryForObject("select correo from usuario where id_usuario=?",String.class,strangerId));
        mvc.perform(put("/api/usuarios/{id}",ownerId).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        payload.put("correo",current.get("correo")); payload.put("tipoDocumento","CE"); payload.put("nroDocumento",jdbc.queryForObject("select nro_documento from usuario where id_usuario=?",String.class,strangerId));
        mvc.perform(put("/api/usuarios/{id}",ownerId).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
    }
    private byte[] image() throws Exception { var out=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(24,24,BufferedImage.TYPE_INT_RGB),"png",out); return out.toByteArray(); }
    private long user(String role,String name,String doc) {
        return jdbc.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,nro_celular,id_rol,fecha_registro) values ('Prueba','Perfil',?,?,?,?,'999111222',(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,doc,doc+suffix+name,name+suffix+"@example.invalid",passwords.encode(secret),role);
    }
    private String login(String name) throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",name+suffix+"@example.invalid","contrasena",secret))))
            .andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer "+json.readTree(response.getContentAsByteArray()).get("token").asText();
    }
}
