package pe.edu.utec.vetcare.integration;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FotoMascotaHttpIT extends PostgresIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    String password, owner, stranger, vet, admin;
    long pet, otherPet;

    @BeforeEach
    void fixture() throws Exception {
        assertEquals("vetcare_p0_integration", jdbc.queryForObject("select current_database()", String.class));
        password = UUID.randomUUID().toString();
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        long ownerId = owner(user("PROPIETARIO", "photo-owner-" + suffix));
        long otherOwner = owner(user("PROPIETARIO", "photo-other-" + suffix));
        long vetUser = user("VETERINARIO", "photo-vet-" + suffix);
        user("ADMIN", "photo-admin-" + suffix);
        long vetId = jdbc.queryForObject("insert into veterinario (id_usuario,especialidad,nro_colegiatura) values (?,'Prueba',?) returning id_veterinario", Long.class, vetUser, suffix);
        pet = pet(ownerId); otherPet = pet(otherOwner);
        jdbc.update("insert into cita (id_veterinario,id_mascota,fecha_cita,estado) values (?,?,current_timestamp,'PENDIENTE')", vetId, pet);
        owner = login("photo-owner-" + suffix); stranger = login("photo-other-" + suffix);
        vet = login("photo-vet-" + suffix); admin = login("photo-admin-" + suffix);
    }

    @Test
    void guardaNormalizaPersisteReemplazaYEliminaSinCambiarFicha() throws Exception {
        mvc.perform(get("/api/mascotas/{id}/foto", pet).header("Authorization", owner)).andExpect(status().isNotFound());
        byte[] first = image("png", 40, 40, Color.RED);
        upload(owner, pet, first, "image/png").andExpect(status().isNoContent());
        byte[] stored = jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet);
        var decoded = ImageIO.read(new java.io.ByteArrayInputStream(stored));
        assertEquals(512, decoded.getWidth()); assertEquals(512, decoded.getHeight());
        mvc.perform(get("/api/mascotas/{id}", pet).header("Authorization", owner)).andExpect(status().isOk()).andExpect(jsonPath("$.tieneFoto").value(true));
        // Lectura HTTP independiente tras commit: la imagen vive en PostgreSQL, no en memoria del servicio.
        mvc.perform(get("/api/mascotas/{id}/foto", pet).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(stored));
        long ownerId = jdbc.queryForObject("select id_propietario from mascota where id_mascota=?", Long.class, pet);
        mvc.perform(put("/api/mascotas/{id}", pet).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idPropietario", ownerId, "nombre", "Mascota de prueba", "especie", "Canino"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tieneFoto").value(true));
        assertArrayEquals(stored, jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet));
        upload(owner, pet, image("jpg", 64, 64, Color.BLUE), "image/jpeg").andExpect(status().isNoContent());
        byte[] replacement = jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet);
        assertFalse(java.util.Arrays.equals(stored, replacement));
        mvc.perform(delete("/api/mascotas/{id}/foto", pet).header("Authorization", owner)).andExpect(status().isNoContent());
        assertNull(jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet));
        mvc.perform(get("/api/mascotas/{id}", pet).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Mascota de prueba"))
                .andExpect(jsonPath("$.tieneFoto").value(false));
    }

    @Test
    void propietarioAjenoNoPuedeConsultarReemplazarNiEliminar() throws Exception {
        upload(owner, pet, image("png", 32, 32, Color.RED), "image/png").andExpect(status().isNoContent());
        byte[] before = jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet);
        mvc.perform(get("/api/mascotas/{id}/foto", pet).header("Authorization", stranger)).andExpect(status().isForbidden());
        upload(stranger, pet, image("png", 32, 32, Color.BLUE), "image/png").andExpect(status().isForbidden());
        mvc.perform(delete("/api/mascotas/{id}/foto", pet).header("Authorization", stranger)).andExpect(status().isForbidden());
        assertArrayEquals(before, jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet));
    }

    @Test
    void veterinarioSoloLeePacientesAutorizadosYAdminGestiona() throws Exception {
        upload(admin, pet, image("png", 32, 32, Color.GREEN), "image/png").andExpect(status().isNoContent());
        mvc.perform(get("/api/mascotas/{id}/foto", pet).header("Authorization", vet)).andExpect(status().isOk());
        mvc.perform(get("/api/mascotas/{id}/foto", otherPet).header("Authorization", vet)).andExpect(status().isForbidden());
        upload(vet, pet, image("png", 32, 32, Color.BLUE), "image/png").andExpect(status().isForbidden());
        mvc.perform(delete("/api/mascotas/{id}/foto", pet).header("Authorization", vet)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/mascotas/{id}/foto", pet).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    @Test
    void rechazaCorruptosFormatoNoPermitidoRectangularYExcesoSinPerderAnterior() throws Exception {
        upload(owner, pet, image("png", 32, 32, Color.RED), "image/png").andExpect(status().isNoContent());
        byte[] before = jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet);
        for (byte[] invalid : new byte[][] { "contenido falso".getBytes(), new byte[]{(byte)137,80,78,71,13,10,26,10},
                image("gif",32,32,Color.RED), image("png",32,64,Color.RED), new byte[5*1024*1024+1] }) {
            upload(owner, pet, invalid, "image/png").andExpect(status().isBadRequest());
            assertArrayEquals(before, jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet));
        }
    }

    @Test
    void fotoNoEsPublica() throws Exception {
        mvc.perform(get("/api/mascotas/{id}/foto", pet)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/mascotas/{id}/foto", pet).file(new MockMultipartFile("foto","avatar.png","image/png",image("png",16,16,Color.RED))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validaWebPRealYMigracionV9() throws Exception {
        byte[] webp;
        try (var input = getClass().getResourceAsStream("/fixtures/pet-avatar.webp")) { assertNotNull(input); webp = input.readAllBytes(); }
        upload(owner, pet, webp, "image/webp").andExpect(status().isNoContent());
        mvc.perform(get("/api/mascotas/{id}/foto", pet).header("Authorization", owner)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where version='9' and success", Integer.class));
    }

    @Test
    void rechazaWebPCorruptoSinGuardar() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/fixtures/pet-avatar.webp")) { assertNotNull(input); bytes = input.readAllBytes(); }
        upload(owner, pet, java.util.Arrays.copyOf(bytes, 16), "image/webp").andExpect(status().isBadRequest());
        assertNull(jdbc.queryForObject("select foto_perfil from mascota where id_mascota=?", byte[].class, pet));
    }

    private org.springframework.test.web.servlet.ResultActions upload(String auth,long id,byte[] bytes,String mime) throws Exception {
        return mvc.perform(multipart("/api/mascotas/{id}/foto", id).file(new MockMultipartFile("foto","avatar",mime,bytes)).header("Authorization",auth));
    }
    private byte[] image(String format,int width,int height,Color color) throws Exception {
        var image = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(color); graphics.fillRect(0,0,width,height); graphics.dispose();
        var output = new ByteArrayOutputStream(); assertTrue(ImageIO.write(image,format,output)); return output.toByteArray();
    }
    private long user(String role,String name) {
        return jdbc.queryForObject("insert into usuario (nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Prueba','Foto','PRUEBA',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario", Long.class, UUID.randomUUID().toString().substring(0,18),name+"@example.invalid",passwords.encode(password),role);
    }
    private long owner(long user) { return jdbc.queryForObject("insert into propietario (id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,user); }
    private long pet(long owner) { return jdbc.queryForObject("insert into mascota (id_propietario,nombre,especie) values (?,'Mascota de prueba','Canino') returning id_mascota",Long.class,owner); }
    private String login(String name) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",name+"@example.invalid","contrasena",password))))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
    }
}
