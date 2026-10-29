package pe.edu.utec.vetcare.integration;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
class AntecedentesMigracionIT extends PostgresIntegrationSupport {
 @Test void v13ConservaResumenV12SinFabricarHistoria() throws Exception {
  String schema="auditoria_"+UUID.randomUUID().toString().replace("-","");
  var before=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(schema).defaultSchema(schema).target("12").load();before.migrate();
  try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())){
   connection.createStatement().execute("set search_path to "+schema);
   var db=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
   long user=db.queryForObject("insert into usuario(nombres,apellidos,tipo_documento,nro_documento,correo,contrasena,id_rol,fecha_registro) values ('Persona','Sintética','PRUEBA','SINTETICO','migracion@example.invalid','no-es-una-credencial-personal',(select id_rol from roles where nombre='PROPIETARIO'),current_timestamp) returning id_usuario",Long.class);
   long owner=db.queryForObject("insert into propietario(id_usuario,fecha_registro_clinica,estado_cliente) values (?,current_date,'ACTIVO') returning id_propietario",Long.class,user);
   long pet=db.queryForObject("insert into mascota(id_propietario,nombre,especie) values (?,'Paciente de migración','Canino') returning id_mascota",Long.class,owner);
   long history=db.queryForObject("insert into historia_clinica(id_mascota,fecha_apertura,notas_medicas) values (?,current_timestamp,?) returning id_historia",Long.class,pet,"Cuidados previos\nCon acentos áéíóúñ");
   var original=db.queryForMap("select * from historia_clinica where id_historia=?",history);
   Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(schema).defaultSchema(schema).load().migrate();
   var actual=db.queryForMap("select * from historia_clinica where id_historia=?",history);for(var entry:original.entrySet())assertEquals(entry.getValue(),actual.get(entry.getKey()));
   assertEquals(0L,db.queryForObject("select version_antecedentes from historia_clinica where id_historia=?",Long.class,history));
   assertEquals(0,db.queryForObject("select count(*) from antecedente_cambio",Integer.class));
   assertEquals(1,db.queryForObject("select count(*) from flyway_schema_history where version='13' and success",Integer.class));
  }
 }
}
