package pe.edu.utec.vetcare.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** JWT y HTTP reales contra PostgreSQL efímero migrado con Flyway. */
class AntecedentesHttpIT extends PostgresIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    String password, owner, stranger, vet, otherVet, admin;
    long pet, otherPet, history, otherHistory;

    @BeforeEach
    void fixture() throws Exception {
        assertEquals("vetcare_p0_integration", jdbc.queryForObject("select current_database()", String.class));
        password = UUID.randomUUID().toString();
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        long ownerId = owner(user("PROPIETARIO", "history-owner-" + suffix));
        long otherOwnerId = owner(user("PROPIETARIO", "history-other-" + suffix));
        long vetUser = user("VETERINARIO", "history-vet-" + suffix);
        long otherVetUser = user("VETERINARIO", "history-vet-other-" + suffix);
        user("ADMIN", "history-admin-" + suffix);
        long vetId = veterinarian(vetUser, suffix), otherVetId = veterinarian(otherVetUser, "o" + suffix);
        pet = pet(ownerId); otherPet = pet(otherOwnerId);
        history = history(pet); otherHistory = history(otherPet);
        jdbc.update("update usuario set nro_celular='000000000' where id_usuario=(select id_usuario from propietario where id_propietario=?)", ownerId);
        jdbc.update("insert into cita (id_veterinario,id_mascota,fecha_cita,estado) values (?,?,current_timestamp,'PENDIENTE')", vetId, pet);
        jdbc.update("insert into cita (id_veterinario,id_mascota,fecha_cita,estado) values (?,?,current_timestamp,'PENDIENTE')", otherVetId, otherPet);
        owner = login("history-owner-" + suffix); stranger = login("history-other-" + suffix);
        vet = login("history-vet-" + suffix); otherVet = login("history-vet-other-" + suffix); admin = login("history-admin-" + suffix);
    }
    @Test
    void consultaPropiaPorHistoriaYPorMascotaConEstadoInicialNeutro() throws Exception {
        for (String path : new String[]{"/api/historias-clinicas/" + history, "/api/historias-clinicas/mascota/" + pet}) {
            mvc.perform(get(path).header("Authorization", owner)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.idMascota").value(pet)).andExpect(jsonPath("$.antecedentes.estadoAlergias").value("NO_EVALUADAS"))
                    .andExpect(jsonPath("$.antecedentes.actualizadoEn").isEmpty())
                    .andExpect(jsonPath("$.telefonoPropietario").value("000000000"));
            mvc.perform(get(path).header("Authorization", vet)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.telefonoPropietario").value("000000000"));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where version='10' and success", Integer.class));
    }
    @Test
    void rechazaConsultaAjenaYAnonimaSinFiltrarAntecedentes() throws Exception {
        for (String path : new String[]{"/api/historias-clinicas/" + history, "/api/historias-clinicas/mascota/" + pet}) {
            mvc.perform(get(path).header("Authorization", stranger)).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", otherVet)).andExpect(status().isForbidden());
        }
    }
    @Test
    void veterinarioAutorizadoPersisteYPropietarioRecuperaTextosIntegros() throws Exception {
        var body = body("ALERGIAS_REGISTRADAS");
        body.put("alergiasSustancias", "Penicilina; reacción confirmada");
        body.put("alergiasReacciones", "Prurito y erupción");
        body.put("alergiasObservaciones", "Evaluación clínica registrada");
        body.put("enfermedadesRelevantes", "Condición crónica: seguimiento periódico");
        body.put("cirugiasPrevias", "Procedimiento previo · 2025-03-10 · evolución favorable");
        body.put("notasMedicas", "Cuidados y precauciones\n" + "áéíóúñ".repeat(500));
        body.put("alertasMedicas", "Evitar el medicamento registrado");
        update(vet, history, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.antecedentes.veterinarioNombre").value("Prueba Historia"))
                .andExpect(jsonPath("$.antecedentes.actualizadoEn").isNotEmpty());
        mvc.perform(get("/api/historias-clinicas/mascota/{id}", pet).header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.antecedentes.notasMedicas").value(body.get("notasMedicas")))
                .andExpect(jsonPath("$.antecedentes.cirugiasPrevias").value(body.get("cirugiasPrevias")))
                .andExpect(jsonPath("$.antecedentes.alergiasReacciones").value(body.get("alergiasReacciones")));
        assertEquals(body.get("notasMedicas"), jdbc.queryForObject("select notas_medicas from historia_clinica where id_historia=?", String.class, history));
        assertNull(jdbc.queryForObject("select notas_medicas from historia_clinica where id_historia=?", String.class, otherHistory));
    }
    @Test
    void propietarioYAdminNoEditanYAdminConservaLectura() throws Exception {
        for (String token : new String[]{owner, stranger, admin, otherVet}) update(token, history, body("SIN_ALERGIAS_CONOCIDAS")).andExpect(status().isForbidden());
        mvc.perform(put("/api/historias-clinicas/{id}/antecedentes", history).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body("NO_EVALUADAS"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/historias-clinicas/{id}", history).header("Authorization", admin)).andExpect(status().isOk());
        assertEquals("NO_EVALUADAS", jdbc.queryForObject("select estado_alergias from historia_clinica where id_historia=?", String.class, history));
    }
    @Test
    void confirmaAusenciaSoloPorVeterinarioYConAuditoria() throws Exception {
        update(vet, history, body("SIN_ALERGIAS_CONOCIDAS")).andExpect(status().isOk());
        mvc.perform(get("/api/historias-clinicas/{id}", history).header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.antecedentes.estadoAlergias").value("SIN_ALERGIAS_CONOCIDAS"))
                .andExpect(jsonPath("$.antecedentes.veterinarioNombre").value("Prueba Historia"))
                .andExpect(jsonPath("$.antecedentes.actualizadoEn").isNotEmpty());
    }
    @Test
    void validaCoherenciaLongitudesYEstadoSinCambiarDatos() throws Exception {
        update(vet, history, body("ALERGIAS_REGISTRADAS")).andExpect(status().isBadRequest());
        var contradictory = body("SIN_ALERGIAS_CONOCIDAS"); contradictory.put("alergiasSustancias", "Penicilina");
        update(vet, history, contradictory).andExpect(status().isBadRequest());
        var excessive = body("NO_EVALUADAS"); excessive.put("alertasMedicas", "á".repeat(2001));
        update(vet, history, excessive).andExpect(status().isBadRequest());
        update(vet, history, Map.of()).andExpect(status().isBadRequest());
        update(vet, history, body("ESTADO_INVALIDO")).andExpect(status().isBadRequest());
        assertEquals("NO_EVALUADAS", jdbc.queryForObject("select estado_alergias from historia_clinica where id_historia=?", String.class, history));
        assertNull(jdbc.queryForObject("select antecedentes_actualizados_en from historia_clinica where id_historia=?", java.sql.Timestamp.class, history));
    }
    @Test
    void cuatroEdicionesConservanOtrasTarjetasYPropietarioLeeCambios() throws Exception {
        var fields = body("ALERGIAS_REGISTRADAS");
        fields.put("alergiasSustancias", "Sustancia sintética\nReacción evaluada");
        fields.put("alergiasReacciones", "Reacción sintética");
        fields.put("alergiasObservaciones", "Observación con áéíóúñ");
        update(vet, history, fields).andExpect(status().isOk());
        String[][] edits = {{"enfermedadesRelevantes", "Condición documentada\nSeguimiento"},
                {"cirugiasPrevias", "Procedimiento histórico\nSin nueva atención"},
                {"notasMedicas", "Cuidados registrados\náéíóúñ"}, {"alertasMedicas", "Alerta documentada"}};
        int before = jdbc.queryForObject("select count(*) from atenciones", Integer.class);
        for (String[] edit : edits) {
            fields.put(edit[0], edit[1]);
            update(vet, history, fields).andExpect(status().isOk());
            var result = mvc.perform(get("/api/historias-clinicas/mascota/{id}", pet).header("Authorization", owner))
                    .andExpect(status().isOk()).andReturn();
            var actual = json.readTree(result.getResponse().getContentAsByteArray()).get("antecedentes");
            for (var field : fields.entrySet()) assertEquals(field.getValue(), actual.get(field.getKey()).asText());
        }
        assertEquals(before, jdbc.queryForObject("select count(*) from atenciones", Integer.class));
        fields.put("cirugiasPrevias", null);
        update(vet, history, fields).andExpect(status().isOk());
        mvc.perform(get("/api/historias-clinicas/{id}", history).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.antecedentes.cirugiasPrevias").isEmpty())
                .andExpect(jsonPath("$.antecedentes.notasMedicas").value(fields.get("notasMedicas")));
    }
    @Test void historialInicialNoFabricaVersionesNiAutoresAntiguos() throws Exception {
        jdbc.update("update historia_clinica set notas_medicas='Texto previo sin autor reconstruido' where id_historia=?",history);
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/historias-clinicas/{id}",history).header("Authorization",owner)).andExpect(jsonPath("$.antecedentes.version").value(0)).andExpect(jsonPath("$.antecedentes.notasMedicas").value("Texto previo sin autor reconstruido"));
    }
    @Test void dosVeterinariosConservanAnteriorNuevoAutorFechaYMotivo() throws Exception {
        var first=notas(0,"Dieta registrada por A",null);auditPut(vet,first).andExpect(status().isOk()).andExpect(jsonPath("$.antecedentes.version").value(1));
        long secondVet=jdbc.queryForObject("select id_veterinario from cita where id_mascota=? limit 1",Long.class,otherPet);
        jdbc.update("insert into cita(id_veterinario,id_mascota,fecha_cita,estado) values (?,?,current_timestamp,'PENDIENTE')",secondVet,pet);
        auditPut(otherVet,notas(1,"Dieta corregida por B","Reevaluación documentada")).andExpect(status().isOk()).andExpect(jsonPath("$.antecedentes.version").value(2));
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",vet)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
         .andExpect(jsonPath("$.items[0].anterior.notasMedicas").value("Dieta registrada por A")).andExpect(jsonPath("$.items[0].nuevo.notasMedicas").value("Dieta corregida por B"))
         .andExpect(jsonPath("$.items[0].idVeterinario").value(secondVet)).andExpect(jsonPath("$.items[0].fecha").isNotEmpty()).andExpect(jsonPath("$.items[0].motivo").value("Reevaluación documentada"));
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].motivo").isEmpty()).andExpect(jsonPath("$.items[1].nuevo.notasMedicas").value("Dieta registrada por A"));
        assertEquals(1,jdbc.queryForObject("select count(*) from historia_clinica where id_historia=?",Integer.class,history));
    }
    @Test void motivoObligatorioAlCorregirOBorrarYVersionObligatoria() throws Exception {
        var missing=body("NO_EVALUADAS");auditPut(vet,missing).andExpect(status().isBadRequest());
        auditPut(vet,notas(0,"Contenido original",null)).andExpect(status().isOk());
        auditPut(vet,notas(1,"Cambio sin motivo",null)).andExpect(status().isBadRequest());
        auditPut(vet,notas(0,"Cliente obsoleto","Corrección")).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("select count(*) from antecedente_cambio where id_historia=?",Integer.class,history));
        auditPut(vet,notas(1,null,"Se retira por corrección clínica")).andExpect(status().isOk());
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",vet)).andExpect(jsonPath("$.items[0].anterior.notasMedicas").value("Contenido original")).andExpect(jsonPath("$.items[0].nuevo.notasMedicas").isEmpty());
    }
    @Test void escrituraConcurrenteNoPierdeInformacionYGeneraUnSoloEvento() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try {
            var one=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return auditPut(vet,notas(0,"Edición A",null)).andReturn().getResponse().getStatus();});
            var two=pool.submit(()->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return auditPut(vet,notas(0,"Edición B",null)).andReturn().getResponse().getStatus();});
            var statuses=new java.util.ArrayList<>(java.util.List.of(one.get(20,java.util.concurrent.TimeUnit.SECONDS),two.get(20,java.util.concurrent.TimeUnit.SECONDS)));java.util.Collections.sort(statuses);assertEquals(java.util.List.of(200,409),statuses);
        }finally{pool.shutdownNow();}
        assertEquals(1L,jdbc.queryForObject("select version_antecedentes from historia_clinica where id_historia=?",Long.class,history));
        assertEquals(1,jdbc.queryForObject("select count(*) from antecedente_cambio where id_historia=?",Integer.class,history));
        assertEquals(jdbc.queryForObject("select notas_medicas from historia_clinica where id_historia=?",String.class,history),jdbc.queryForObject("select nuevo->>'notasMedicas' from antecedente_cambio where id_historia=?",String.class,history));
    }
    @Test void historialInmutableYPaginadoConPermisosDelExpediente() throws Exception {
        auditPut(vet,notas(0,"Primera versión",null)).andExpect(status().isOk());auditPut(vet,notas(1,"Segunda versión","Seguimiento")).andExpect(status().isOk());
        for(String token:new String[]{stranger,otherVet})mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",admin).param("size","1").param("pagina","1").param("seccion","cuidados")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].revision").value(1));
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",vet).param("seccion","incorrecta")).andExpect(status().isBadRequest());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update antecedente_cambio set motivo='alterado' where id_historia=?",history));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from antecedente_cambio where id_historia=?",history));
        assertEquals(2,jdbc.queryForObject("select count(*) from antecedente_cambio where id_historia=?",Integer.class,history));
    }
    @Test void atencionRelacionadaDebePertenecerAPacienteYVeterinario() throws Exception {
        long vetId=jdbc.queryForObject("select id_veterinario from cita where id_mascota=? limit 1",Long.class,pet);
        long cita=jdbc.queryForObject("insert into cita(id_mascota,id_veterinario,fecha_cita,estado) values (?,?,current_timestamp,'ATENDIDA') returning id_cita",Long.class,pet,vetId);
        long atencion=jdbc.queryForObject("insert into atenciones(id_historia,id_veterinario_atencion,id_cita,fecha_atencion) values (?,?,?,current_timestamp) returning id_atencion",Long.class,history,vetId,cita);
        var linked=notas(0,"Cuidados vinculados",null);linked.put("idAtencion",atencion);auditPut(vet,linked).andExpect(status().isOk());
        mvc.perform(get("/api/historias-clinicas/{id}/antecedentes/cambios",history).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].idAtencion").value(atencion));
        var bad=notas(0,"Historia incorrecta",null);bad.put("idAtencion",atencion);
        // El segundo veterinario puede editar su paciente, pero no enlazar la atención de otro.
        mvc.perform(put("/api/historias-clinicas/{id}/antecedentes",otherHistory).header("Authorization",otherVet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad))).andExpect(status().isForbidden());
        assertEquals(0,jdbc.queryForObject("select count(*) from antecedente_cambio where id_historia=?",Integer.class,otherHistory));
        assertEquals(0L,jdbc.queryForObject("select version_antecedentes from historia_clinica where id_historia=?",Long.class,otherHistory));
    }
    @Test void sinCambiosNoCreaEventoNiAvanzaVersion() throws Exception {
        var noChange=body("NO_EVALUADAS");noChange.put("version",0);noChange.put("notasMedicas","   ");noChange.put("alertasMedicas","");auditPut(vet,noChange).andExpect(status().isOk()).andExpect(jsonPath("$.antecedentes.version").value(0));
        assertEquals(0,jdbc.queryForObject("select count(*) from antecedente_cambio where id_historia=?",Integer.class,history));
    }
    private Map<String,Object> notas(long version,String text,String motivo){var body=body("NO_EVALUADAS");body.put("version",version);body.put("notasMedicas",text);body.put("motivo",motivo);return body;}
    private org.springframework.test.web.servlet.ResultActions auditPut(String token,Map<String,?> body) throws Exception {return mvc.perform(put("/api/historias-clinicas/{id}/antecedentes",history).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
    private Map<String,Object> body(String state) { var body = new HashMap<String,Object>(); body.put("estadoAlergias", state); return body; }
    private org.springframework.test.web.servlet.ResultActions update(String token, long id, Map<String,?> body) throws Exception {
        var payload=new HashMap<String,Object>(body);payload.put("version",jdbc.queryForObject("select version_antecedentes from historia_clinica where id_historia=?",Long.class,id));payload.put("motivo","Corrección sintética de la prueba");
        return mvc.perform(put("/api/historias-clinicas/{id}/antecedentes", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }
    private long user(String role, String name) { return jdbc.queryForObject("insert into usuario (nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Prueba','Historia','PRUEBA',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario", Long.class, UUID.randomUUID().toString().substring(0,18), name + "@example.invalid", passwords.encode(password), role); }
    private long owner(long user) { return jdbc.queryForObject("insert into propietario (id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario", Long.class, user); }
    private long veterinarian(long user, String number) { return jdbc.queryForObject("insert into veterinario (id_usuario,especialidad,nro_colegiatura) values (?,'Prueba',?) returning id_veterinario", Long.class,user,number); }
    private long pet(long owner) { return jdbc.queryForObject("insert into mascota (id_propietario,nombre,especie) values (?,'Mascota sintética','Canino') returning id_mascota",Long.class,owner); }
    private long history(long pet) { return jdbc.queryForObject("insert into historia_clinica (id_mascota,fecha_apertura) values (?,current_timestamp) returning id_historia",Long.class,pet); }
    private String login(String name) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",name+"@example.invalid","contrasena",password))))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
    }
}
