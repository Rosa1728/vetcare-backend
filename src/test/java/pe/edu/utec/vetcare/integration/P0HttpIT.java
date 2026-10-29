package pe.edu.utec.vetcare.integration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.utec.vetcare.enums.EstadoCita;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP real de Spring MVC + filtros JWT + servicios + Flyway/PostgreSQL, sin mocks. */
@org.springframework.context.annotation.Import(CitasClinicasHttpIT.TimeConfig.class)
class P0HttpIT extends PostgresIntegrationSupport {
    private static final List<String> CAMPOS = List.of("sintoma", "diagnostico", "tratamiento", "observacion");
    @Autowired CitasClinicasHttpIT.MutableClock clock;
    private LocalDateTime testNow(){return LocalDateTime.ofInstant(clock.instant(),java.time.ZoneId.of("America/Lima"));}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    private String prefix;
    private String password;
    private String adminEmail;
    private String vetEmail;
    private String ownerEmail;
    private String adminToken;
    private long vetId;
    private long petId;
    private long historyId;

    @BeforeEach
    void syntheticFixture() throws Exception {
        clock.at("2030-01-10T10:10:00");
        assertEquals("vetcare_p0_integration", jdbc.queryForObject("select current_database()", String.class));
        prefix = "P0-" + UUID.randomUUID().toString().substring(0, 12);
        password = UUID.randomUUID().toString();
        adminEmail = prefix + "-admin@example.invalid";
        vetEmail = prefix + "-vet@example.invalid";
        ownerEmail = prefix + "-owner@example.invalid";
        user("ADMIN", adminEmail, "A");
        long vetUser = user("VETERINARIO", vetEmail, "V");
        long ownerUser = user("PROPIETARIO", ownerEmail, "P");
        long ownerId = jdbc.queryForObject("""
                insert into propietario (id_usuario, fecha_registro_clinica, estado_cliente)
                values (?, current_date, 'ACTIVO') returning id_propietario
                """, Long.class, ownerUser);
        vetId = jdbc.queryForObject("""
                insert into veterinario (id_usuario, especialidad, nro_colegiatura)
                values (?, 'Prueba P0', ?) returning id_veterinario
                """, Long.class, vetUser, prefix);
        adminToken = login(adminEmail);
        mvc.perform(get("/api/usuarios/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMIN"));
        JsonNode pet = body(mvc.perform(post("/api/mascotas")
                .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idPropietario", ownerId,
                        "nombre", prefix, "especie", "Canino"))))
                .andExpect(status().isCreated()).andReturn());
        petId = pet.get("idMascota").asLong();
        historyId = body(mvc.perform(get("/api/historias-clinicas/mascota/{id}", petId)
                .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn()).get("idHistoria").asLong();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"ATENDIDA", "CANCELADA"})
    void adminRecibe409YLaCitaCerradaPermaneceIntacta(EstadoCita estado) throws Exception {
        long cita = newAppointment();
        Long atencion = null;
        if (estado == EstadoCita.CANCELADA) {
            mvc.perform(put("/api/citas/{id}/cancelar", cita).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Motivos personales.\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        } else {
            confirm(cita);
            atencion = createAttention(cita, clinicalTexts(500, "creación"));
        }
        JsonNode before = appointment(cita);
        Map<String, Object> persistedBefore = jdbc.queryForMap("select * from cita where id_cita = ?", cita);
        JsonNode attentionBefore = atencion == null ? null : attention(atencion);
        assertEquals(estado.name(), before.get("estado").asText());

        mvc.perform(put("/api/citas/{id}", cita).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reschedule())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No se puede reprogramar una cita " + estado));

        assertEquals(before.get("estado"), appointment(cita).get("estado"));
        assertEquals(persistedBefore, jdbc.queryForMap("select * from cita where id_cita = ?", cita));
        if (atencion != null) assertEquals(attentionBefore, attention(atencion));
    }

    @Test
    void guardaYRecupera500CaracteresConAcentosAlCrearYActualizar() throws Exception {
        long cita = newAppointment();
        confirm(cita);
        Map<String, Object> texts = clinicalTexts(500, "creación");
        long id = createAttention(cita, texts);
        verifyPersistedTexts(id, texts);
        assertEquals("ATENDIDA", appointment(cita).get("estado").asText());

        Map<String, Object> updated = clinicalTexts(500, "edición");
        updated.remove("precioAtencion"); // La edición clínica del autor no modifica precios administrativos.
        mvc.perform(put("/api/atenciones/{id}", id).header("Authorization", bearer(login(vetEmail)))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updated)))
                .andExpect(status().isOk());
        verifyPersistedTexts(id, updated);
    }

    @Test
    void rechaza501CaracteresSinInsertarAtencionNiCerrarCita() throws Exception {
        long cita = newAppointment();
        confirm(cita);
        Map<String, Object> payload = createPayload(cita, clinicalTexts(501, "límite"));
        var result = mvc.perform(post("/api/atenciones").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        for (String campo : CAMPOS) result.andExpect(jsonPath("$.campos." + campo).exists());
        assertEquals(0L, jdbc.queryForObject("select count(*) from atenciones where id_cita = ?", Long.class, cita));
        assertEquals("CONFIRMADA", appointment(cita).get("estado").asText());
    }

    @Test
    void rechazaEscriturasSinTokenOConTokenInvalido() throws Exception {
        long cita = newAppointment();
        String payload = json.writeValueAsString(reschedule());
        mvc.perform(put("/api/citas/{id}", cita).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/citas/{id}", cita).header("Authorization", "Bearer invalid-test-token")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        assertEquals("PENDIENTE", appointment(cita).get("estado").asText());
    }

    @Test
    void veterinarioNoPuedeReprogramarYPropietarioNoPuedeEscribirAtenciones() throws Exception {
        long cita = newAppointment();
        String vetToken = login(vetEmail);
        mvc.perform(put("/api/citas/{id}", cita).header("Authorization", bearer(vetToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reschedule())))
                .andExpect(status().isForbidden());
        confirm(cita);
        String ownerToken = login(ownerEmail);
        mvc.perform(post("/api/atenciones").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(createPayload(cita, clinicalTexts(500, "permiso")))))
                .andExpect(status().isForbidden());
        assertEquals(0L, jdbc.queryForObject("select count(*) from atenciones where id_cita = ?", Long.class, cita));
        assertEquals("CONFIRMADA", appointment(cita).get("estado").asText());
    }

    @Test
    void loginRechazaCredencialesSinteticasIncorrectas() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", adminEmail,
                        "contrasena", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conservaReprogramacionAdminDeCitaPendiente() throws Exception {
        long cita = newAppointment();
        Map<String, Object> request = reschedule();
        mvc.perform(put("/api/citas/{id}", cita).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        JsonNode saved = appointment(cita);
        assertEquals(request.get("motivoCita"), saved.get("motivoCita").asText());
        assertEquals(LocalDateTime.parse((String) request.get("fechaCita")),
                LocalDateTime.parse(saved.get("fechaCita").asText()));
    }

    @Test
    void flywayAplicaV1AV8YLasCuatroColumnasSonDe500() {
        assertEquals(8L, jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where success and version in ('1','2','3','4','5','6','7','8')
                """, Long.class));
        for (String campo : CAMPOS) {
            assertEquals(500, jdbc.queryForObject("""
                    select character_maximum_length from information_schema.columns
                    where table_schema = 'public' and table_name = 'atenciones' and column_name = ?
                    """, Integer.class, campo));
        }
    }

    @Test
    void flujoCitasPropietarioVeterinarioPersistidoYSinAtencionDuplicada() throws Exception {
        String owner = bearer(login(ownerEmail)), vet = bearer(login(vetEmail));
        var start = testNow().plusDays(4).withMinute(0).truncatedTo(ChronoUnit.SECONDS);
        var request = Map.of("idMascota", petId, "idVeterinario", vetId, "fechaCita", start.toString(), "motivoCita", "Control sintético con acentos áéíóúñ");
        long id = body(mvc.perform(post("/api/citas").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE")).andReturn()).get("idCita").asLong();
        mvc.perform(get("/api/citas").header("Authorization", vet)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idCita == " + id + ")].estado").value(org.hamcrest.Matchers.contains("PENDIENTE")));
        mvc.perform(post("/api/citas").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isConflict());
        var updated = Map.of("fechaCita", start.plusDays(1).toString(), "motivoCita", "Control reprogramado");
        mvc.perform(put("/api/citas/{id}", id).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updated))).andExpect(status().isOk());
        assertEquals(start.plusDays(1), jdbc.queryForObject("select fecha_cita from cita where id_cita=?", java.sql.Timestamp.class, id).toLocalDateTime());
        mvc.perform(put("/api/citas/{id}/confirmar", id).header("Authorization", owner)).andExpect(status().isForbidden());
        mvc.perform(put("/api/citas/{id}/confirmar", id).header("Authorization", vet)).andExpect(status().isOk());
        mvc.perform(get("/api/citas/{id}", id).header("Authorization", owner)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mvc.perform(put("/api/citas/{id}", id).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updated))).andExpect(status().isForbidden());

        var bad = createPayload(id, clinicalTexts(501, "rechazo"));
        mvc.perform(post("/api/atenciones").header("Authorization", vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad))).andExpect(status().isBadRequest());
        assertEquals("CONFIRMADA", appointment(id).get("estado").asText());
        jdbc.update("update cita set fecha_cita=? where id_cita=?", testNow().minusMinutes(5), id);
        var payload = createPayload(id, clinicalTexts(100, "registro"));
        payload.remove("precioAtencion");
        long att = body(mvc.perform(post("/api/atenciones").header("Authorization", vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isOk()).andReturn()).get("idAtencion").asLong();
        mvc.perform(get("/api/atenciones/{id}", att).header("Authorization", owner)).andExpect(status().isOk()).andExpect(jsonPath("$.idCita").value(id));
        mvc.perform(get("/api/citas/{id}", id).header("Authorization", owner)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATENDIDA"));
        mvc.perform(post("/api/atenciones").header("Authorization", vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isConflict());
        assertEquals(1L, jdbc.queryForObject("select count(*) from atenciones where id_cita=?", Long.class, id));
        mvc.perform(put("/api/citas/{id}/cancelar", id).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Motivos personales.\"}")).andExpect(status().isConflict());
        long cancelled = newAppointment();
        mvc.perform(put("/api/citas/{id}/cancelar", cancelled).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Motivos personales.\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/citas/{id}", cancelled).header("Authorization", vet)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void citasAjenaSonInaccesiblesPorIdYEscritura() throws Exception {
        String outsiderEmail=prefix+"-outsider@example.invalid";
        user("PROPIETARIO",outsiderEmail,"O");
        String token=bearer(login(outsiderEmail));long id=newAppointment();
        mvc.perform(get("/api/citas/{id}",id).header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/citas/{id}/cancelar",id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Motivos personales.\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/citas/{id}",id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(reschedule()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/citas").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idMascota",petId,"idVeterinario",vetId,"fechaCita",testNow().plusDays(8).toString(),"motivoCita","Intento ajeno")))).andExpect(status().isForbidden());
        String otherVetEmail=prefix+"-other-vet@example.invalid";
        long otherVetUser=user("VETERINARIO",otherVetEmail,"W");
        jdbc.update("insert into veterinario (id_usuario,especialidad,nro_colegiatura) values (?, 'Prueba', ?)",otherVetUser,prefix+"W");
        String otherVet=bearer(login(otherVetEmail));
        mvc.perform(get("/api/citas/{id}",id).header("Authorization",otherVet)).andExpect(status().isForbidden());
        mvc.perform(get("/api/citas").header("Authorization",otherVet)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/citas/{id}/confirmar",id).header("Authorization",otherVet)).andExpect(status().isForbidden());
        mvc.perform(post("/api/atenciones").header("Authorization",otherVet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(createPayload(id,clinicalTexts(50,"ajeno"))))).andExpect(status().isForbidden());
        assertEquals("PENDIENTE",appointment(id).get("estado").asText());
    }

    private long user(String role, String email, String suffix) {
        return jdbc.queryForObject("""
                insert into usuario (nombres, apellidos, tipo_documento, nro_documento,
                                     correo, contrasena, id_rol, fecha_registro)
                values (?, 'Integración', 'PRUEBA', ?, ?, ?,
                        (select id_rol from roles where nombre = ?), current_timestamp)
                returning id_usuario
                """, Long.class, prefix, prefix + suffix, email, passwords.encode(password), role);
    }

    private String login(String email) throws Exception {
        // Sin print()/logging de solicitudes: los tokens y claves solo viven en memoria.
        JsonNode response = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", email, "contrasena", password))))
                .andExpect(status().isOk()).andReturn());
        assertTrue(response.hasNonNull("token"), "Login debe emitir un token");
        return response.get("token").asText();
    }

    private long newAppointment() throws Exception {
        return body(mvc.perform(post("/api/citas").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "idMascota", petId, "idVeterinario", vetId,
                        "fechaCita", testNow().plusDays(2).withMinute(0).truncatedTo(ChronoUnit.SECONDS).toString(),
                        "motivoCita", prefix + " consulta sintética"))))
                .andExpect(status().isOk()).andReturn()).get("idCita").asLong();
    }

    private void confirm(long cita) throws Exception {
        mvc.perform(put("/api/citas/{id}/confirmar", cita).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    private long createAttention(long cita, Map<String, Object> texts) throws Exception {
        jdbc.update("update cita set fecha_cita=? where id_cita=?",testNow().minusMinutes(5),cita);
        return body(mvc.perform(post("/api/atenciones").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(createPayload(cita, texts))))
                .andExpect(status().isOk()).andReturn()).get("idAtencion").asLong();
    }

    private Map<String, Object> createPayload(long cita, Map<String, Object> texts) {
        Map<String, Object> payload = new LinkedHashMap<>(texts);
        payload.put("idCita", cita);
        payload.put("idHistoria", historyId);
        payload.put("idVeterinario", vetId);
        return payload;
    }

    private Map<String, Object> clinicalTexts(int length, String label) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("precioAtencion", new BigDecimal("1.00"));
        payload.put("peso", new BigDecimal("1.00"));
        for (String campo : CAMPOS) {
            String text = prefix + " " + label + " " + campo + " áéíóúñ ";
            text += "áéíóúñ".repeat(length);
            payload.put(campo, text.substring(0, length));
        }
        return payload;
    }

    private Map<String, Object> reschedule() {
        return Map.of("fechaCita", testNow().plusDays(4).withMinute(0).truncatedTo(ChronoUnit.SECONDS).toString(),
                "motivoCita", prefix + " intento de reprogramación");
    }

    private JsonNode appointment(long id) throws Exception {
        return body(mvc.perform(get("/api/citas/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode attention(long id) throws Exception {
        return body(mvc.perform(get("/api/atenciones/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn());
    }

    private void verifyPersistedTexts(long id, Map<String, Object> expected) throws Exception {
        // Sin @Transactional: POST/PUT ya hicieron commit; GET y JDBC son lecturas independientes.
        JsonNode recovered = attention(id);
        for (String campo : CAMPOS) {
            String value = recovered.get(campo).asText();
            assertEquals(500, value.length(), campo);
            assertEquals(expected.get(campo), value, campo);
            assertEquals(expected.get(campo), jdbc.queryForObject(
                    "select " + campo + " from atenciones where id_atencion = ?", String.class, id));
            assertEquals(500, jdbc.queryForObject(
                    "select char_length(" + campo + ") from atenciones where id_atencion = ?", Integer.class, id));
        }
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) { return "Bearer " + token; }
}
