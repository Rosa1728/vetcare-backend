package pe.edu.utec.vetcare.integration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
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

class VacunasHttpIT extends PostgresIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    String password, owner, stranger, vet, otherVet, admin;
    long pet, otherPet, history, vetId, otherVetId, attention, foreignAttention;
    LocalDate today;
    @BeforeEach void fixture() throws Exception {
        assertEquals("vetcare_p0_integration", jdbc.queryForObject("select current_database()",String.class));
        today=LocalDate.now(ZoneId.of("America/Lima"));password=UUID.randomUUID().toString();String key=UUID.randomUUID().toString().substring(0,10);
        long ownerId=owner(user("PROPIETARIO","owner-"+key)),otherOwner=owner(user("PROPIETARIO","other-"+key));
        vetId=veterinarian(user("VETERINARIO","vet-"+key),key);otherVetId=veterinarian(user("VETERINARIO","other-vet-"+key),"o"+key);user("ADMIN","admin-"+key);
        pet=pet(ownerId);otherPet=pet(otherOwner);history=history(pet);long otherHistory=history(otherPet);
        attention=attention(pet,history,vetId);foreignAttention=attention(pet,history,otherVetId);attention(otherPet,otherHistory,otherVetId);
        owner=login("owner-"+key);stranger=login("other-"+key);vet=login("vet-"+key);otherVet=login("other-vet-"+key);admin=login("admin-"+key);
        mvc.perform(get("/api/usuarios/me").header("Authorization",vet)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("VETERINARIO"));
        mvc.perform(get("/api/usuarios/me").header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("PROPIETARIO"));
    }
    @Test void cicloPersistidoVeterinarioPropietarioEHistoriaSinDuplicar() throws Exception {
        long id=schedule();String notes="áéíóúñ".repeat(83)+"áé";assertEquals(500,notes.length());
        mvc.perform(put("/api/vacunas/{id}/programacion",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("nombre","Antirrábica","proximaDosis",today.toString(),"observaciones",notes))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idVacuna").value(id));
        mvc.perform(get("/api/vacunas/mascota/{id}",pet).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.idVacuna == "+id+")].observaciones").value(org.hamcrest.Matchers.contains(notes)));
        mvc.perform(get("/api/historias-clinicas/mascota/{id}",pet).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.idHistoria").value(history));
        apply(vet,id,attention).andExpect(status().isOk()).andExpect(jsonPath("$.estadoAplicacion").value("APLICADA")).andExpect(jsonPath("$.idVacuna").value(id));
        mvc.perform(patch("/api/vacunas/{id}/observaciones",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("observaciones",notes))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fechaAplicacion").value(today.toString()));
        mvc.perform(get("/api/vacunas/{id}",id).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.observaciones").value(notes)).andExpect(jsonPath("$.estadoAplicacion").value("APLICADA"));
        mvc.perform(get("/api/vacunas/mascota/{id}",pet).header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.idVacuna == "+id+")].estadoAplicacion").value(org.hamcrest.Matchers.contains("APLICADA"))).andExpect(jsonPath("$[?(@.idVacuna == "+id+")].observaciones").value(org.hamcrest.Matchers.contains(notes)));
        assertEquals(1,jdbc.queryForObject("select count(*) from vacuna where id_vacuna=?",Integer.class,id));
        assertEquals(notes,jdbc.queryForObject("select observaciones from vacuna where id_vacuna=?",String.class,id));
        assertEquals(attention,jdbc.queryForObject("select id_atencion from vacuna where id_vacuna=?",Long.class,id));
    }
    @Test void rechazaEscrituraPorPropietarioAdminYOtroVeterinario() throws Exception {
        long id=schedule();
        mvc.perform(get("/api/vacunas/{id}",id).header("Authorization",otherVet)).andExpect(status().isOk());
        mvc.perform(get("/api/vacunas/mascota/{id}",pet).header("Authorization",otherVet)).andExpect(status().isOk());
        for(String token:List.of(owner,admin,otherVet)){
            mvc.perform(put("/api/vacunas/{id}/programacion",id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("nombre","Cambio","proximaDosis",today.plusDays(50).toString())))).andExpect(status().isForbidden());
            apply(token,id,attention).andExpect(status().isForbidden());
            mvc.perform(patch("/api/vacunas/{id}/observaciones",id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"observaciones\":\"Cambio\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/vacunas").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(scheduleBody()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/vacunas/{id}",id).header("Authorization",admin)).andExpect(status().isOk());
        assertEquals("Antirrábica",jdbc.queryForObject("select nombre from vacuna where id_vacuna=?",String.class,id));
    }
    @Test void pertenenciaEnGetPorIdYPorMascotaYAnonimo() throws Exception {
        long id=schedule();
        mvc.perform(get("/api/vacunas/{id}",id).header("Authorization",stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/vacunas/mascota/{id}",pet).header("Authorization",stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/vacunas/mascota/{id}",otherPet).header("Authorization",vet)).andExpect(status().isForbidden());
        mvc.perform(get("/api/vacunas/{id}",id)).andExpect(status().isUnauthorized());
        var forbidden=scheduleBody();forbidden.put("idMascota",otherPet);
        mvc.perform(post("/api/vacunas").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(forbidden))).andExpect(status().isForbidden());
        mvc.perform(get("/api/vacunas/{id}",Long.MAX_VALUE).header("Authorization",vet)).andExpect(status().isNotFound());
    }
    @Test void validaFechasAtencionNotasYTransiciones() throws Exception {
        long id=schedule();
        apply(vet,id,foreignAttention).andExpect(status().isBadRequest());
        mvc.perform(put("/api/vacunas/{id}/programacion",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("nombre","Vacuna","proximaDosis",today.minusDays(1).toString())))).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/vacunas/{id}/observaciones",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        apply(vet,id,attention).andExpect(status().isOk());
        apply(vet,id,attention).andExpect(status().isConflict());
        mvc.perform(put("/api/vacunas/{id}/programacion",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("nombre","Otra","proximaDosis",today.plusDays(30).toString())))).andExpect(status().isConflict());
        mvc.perform(patch("/api/vacunas/{id}/observaciones",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("observaciones","á".repeat(501))))).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/vacunas/{id}/observaciones",id).header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content("{\"observaciones\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.observaciones").isEmpty());
    }
    @Test void dosConfirmacionesConcurrentesSoloAplicanUnaVez() throws Exception {
        long id=schedule();var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Integer> task=()->{barrier.await(10,TimeUnit.SECONDS);return apply(vet,id,attention).andReturn().getResponse().getStatus();};
            var a=pool.submit(task);var b=pool.submit(task);var statuses=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        }finally{pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("select count(*) from vacuna where id_vacuna=? and fecha_aplicacion is not null",Integer.class,id));
    }
    @Test void aplicacionDirectaConcurrenteNoDuplicaLaMismaVacunaEnLaAtencion() throws Exception {
        var body=new HashMap<>(scheduleBody());body.put("nombre","Vacuna directa");body.put("idAtencion",attention);body.put("fechaAplicacion",today.toString());
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Integer> task=()->{barrier.await(10,TimeUnit.SECONDS);return mvc.perform(post("/api/vacunas").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();};
            var a=pool.submit(task);var b=pool.submit(task);var statuses=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        }finally{pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("select count(*) from vacuna where id_atencion=? and nombre='Vacuna directa'",Integer.class,attention));
    }
    private Map<String,Object> scheduleBody(){return new HashMap<>(Map.of("idMascota",pet,"idVeterinario",vetId,"nombre","Antirrábica","proximaDosis",today.toString(),"observaciones","Traer cartilla registrada."));}
    private long schedule() throws Exception {var r=mvc.perform(post("/api/vacunas").header("Authorization",vet).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(scheduleBody()))).andExpect(status().isOk()).andReturn();return json.readTree(r.getResponse().getContentAsByteArray()).get("idVacuna").asLong();}
    private org.springframework.test.web.servlet.ResultActions apply(String token,long id,long att) throws Exception {return mvc.perform(put("/api/vacunas/{id}/aplicar",id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idAtencion",att,"fechaAplicacion",today.toString(),"proximaDosis",today.plusDays(90).toString(),"observaciones","Observación registrada\nSeguimiento clínico."))));}
    private long user(String role,String name){return jdbc.queryForObject("insert into usuario (nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Cuenta','Vacunas','PRUEBA',?,?,?,(select id_rol from roles where nombre=?),current_timestamp) returning id_usuario",Long.class,UUID.randomUUID().toString().substring(0,18),name+"@example.invalid",passwords.encode(password),role);}
    private long owner(long user){return jdbc.queryForObject("insert into propietario (id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,user);}
    private long veterinarian(long user,String number){return jdbc.queryForObject("insert into veterinario (id_usuario,especialidad,nro_colegiatura) values (?,'Prueba',?) returning id_veterinario",Long.class,user,number);}
    private long pet(long owner){return jdbc.queryForObject("insert into mascota (id_propietario,nombre,especie,fecha_nacimiento) values (?,'Paciente sintético','Canino',current_date-365) returning id_mascota",Long.class,owner);}
    private long history(long pet){return jdbc.queryForObject("insert into historia_clinica (id_mascota,fecha_apertura) values (?,current_timestamp) returning id_historia",Long.class,pet);}
    private long attention(long pet,long hist,long veterinarian){long cita=jdbc.queryForObject("insert into cita (id_veterinario,id_mascota,fecha_cita,estado) values (?,?,?,'ATENDIDA') returning id_cita",Long.class,veterinarian,pet,today.atTime(10,0));return jdbc.queryForObject("insert into atenciones (id_historia,id_veterinario_atencion,id_cita,fecha_atencion,peso,diagnostico) values (?,?,?,?,12.5,'Evaluación registrada') returning id_atencion",Long.class,hist,veterinarian,cita,today.atTime(10,0));}
    private String login(String name) throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",name+"@example.invalid","contrasena",password)))).andExpect(status().isOk()).andReturn();
        return "Bearer "+json.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
    }

}
