package pe.edu.utec.vetcare.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utec.vetcare.dto.request.*;
import pe.edu.utec.vetcare.entity.*;
import pe.edu.utec.vetcare.mapper.*;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.service.impl.*;
import pe.edu.utec.vetcare.exception.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Servicios, DTO y mappers reales; repositorios simulados, ninguna conexión a BD. */
class EdicionProtegidaTest {
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 void rol(String r){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("qa@example.invalid","unused",List.of(new SimpleGrantedAuthority("ROLE_"+r))));}
 @ParameterizedTest @ValueSource(strings={"nombres","apellidos","tipoDocumento","nroDocumento","correo","contrasena","idRol","rol","idUsuario","direccionContacto","nroColegiatura","desconocido"})
 void rechazaCamposProtegidosInclusoNull(String field){
  var json=new ObjectMapper();
  for(String value:List.of("null","\"manipulado\"")){
   String body="{\"nroCelular\":\"987654321\",\""+field+"\":"+value+"}";
   assertThrows(Exception.class,()->json.readValue(body,ActualizarUsuarioRequest.class));
   assertThrows(Exception.class,()->json.readValue(body,ContactoAdminRequest.class));
   assertThrows(Exception.class,()->json.readValue("{\"especialidad\":\"General\",\"datosUsuario\":"+body+"}",UpdateVeterinarioRequest.class));
  }
 }
 @Test void controladorRechazaJsonManipuladoSinInvocarServicio() throws Exception {
  var service=mock(PropietarioService.class);
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   var userService=mock(UsuarioService.class);var vetService=mock(VeterinarioService.class);
   var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
    new pe.edu.utec.vetcare.controller.PropietarioController(service,factory.getValidator()),
    new pe.edu.utec.vetcare.controller.UsuarioController(userService,mock(UsuarioRepository.class),Mappers.getMapper(UsuarioMapper.class),mock(RolRepository.class),mock(FotoPerfilService.class),mock(PropietarioRepository.class),mock(CambioContrasenaService.class)),
    new pe.edu.utec.vetcare.controller.VeterinarioController(vetService,factory.getValidator(),mock(FotoPerfilService.class))).setControllerAdvice(new GlobalExceptionHandler()).build();
   for(String field:List.of("nombres","apellidos","tipoDocumento","nroDocumento","correo","contrasena","idRol","rol","idUsuario")){
    for(String path:List.of("/api/propietarios/10","/api/usuarios/10","/api/veterinarios/10")){
     String body="{\"nroCelular\":\"987654321\",\""+field+"\":null}";
     if(path.contains("veterinarios"))body="{\"especialidad\":\"General\",\"datosUsuario\":"+body+"}";
     mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").exists());
    }
   }
   verifyNoInteractions(service,userService,vetService);
  }
 }
 @Test void perfilesPropiosNoAceptanIdentidadNiTratamientoYRestringenSujeto(){
  var users=mock(UsuarioRepository.class);var owners=mock(PropietarioRepository.class);var vets=mock(VeterinarioRepository.class);
  var vs=new VeterinarioServiceImpl(vets,users,Mappers.getMapper(VeterinarioMapper.class),mock(RolRepository.class),new BCryptPasswordEncoder(),mock(UsuarioService.class));
  var os=new PropietarioServiceImpl(owners,users,Mappers.getMapper(PropietarioMapper.class),Mappers.getMapper(UsuarioMapper.class),mock(MascotaRepository.class));
  rol("VETERINARIO");assertThrows(AccessDeniedException.class,()->vs.miPerfil("ajeno@example.invalid"));assertThrows(AccessDeniedException.class,os::obtenerMio);verifyNoInteractions(users,owners,vets);
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   var ps=mock(PropietarioService.class);var pc=new pe.edu.utec.vetcare.controller.PropietarioController(ps,factory.getValidator());
   var svc=mock(VeterinarioService.class);var vc=new pe.edu.utec.vetcare.controller.VeterinarioController(svc,factory.getValidator(),mock(FotoPerfilService.class));
   var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("unit-only").header("alg","none").subject("qa@example.invalid").build();
   for(String field:List.of("nombres","apellidos","correo","tipoDocumento","nroDocumento","contrasena","idRol","idUsuario","tratamientoProfesional","especialidad","nroColegiatura")){
    var body=new HashMap<String,Object>();body.put("nroCelular","987654321");body.put(field,null);
    assertThrows(BadRequestException.class,()->pc.actualizarMiPerfil(body));assertThrows(BadRequestException.class,()->vc.actualizarMiPerfil(jwt,body));
   }
   verifyNoInteractions(ps,svc);
  }
 }
 @Test void administradorEditaContactoProfesionalSinTocarColegiaturaYPerfilPropioLoLee(){
  rol("ADMIN");var users=mock(UsuarioRepository.class);var vets=mock(VeterinarioRepository.class);var owners=mock(PropietarioRepository.class);var role=new RolEntity();role.setNombre("VETERINARIO");
  var u=new UsuarioEntity();u.setIdUsuario(1L);u.setRol(role);u.setNombres("Profesional");u.setCorreo("qa@example.invalid");u.setNroDocumento("12345678");u.setContrasena("hash conservado");
  var v=new VeterinarioEntity();v.setIdVeterinario(10L);v.setUsuario(u);v.setNroColegiatura("QA-001");
  when(users.findById(1L)).thenReturn(Optional.of(u));when(users.findByCorreo(u.getCorreo())).thenReturn(Optional.of(u));when(vets.findById(10L)).thenReturn(Optional.of(v));when(vets.findByUsuario_IdUsuario(1L)).thenReturn(Optional.of(v));when(vets.save(any())).thenAnswer(c->c.getArgument(0));
  var us=new UsuarioServiceImpl(users,mock(RolRepository.class),Mappers.getMapper(UsuarioMapper.class),new BCryptPasswordEncoder(),owners,vets);
  var vs=new VeterinarioServiceImpl(vets,users,Mappers.getMapper(VeterinarioMapper.class),mock(RolRepository.class),new BCryptPasswordEncoder(),us);
  var r=new UpdateVeterinarioRequest();r.setEspecialidad("Cirugía");r.setTratamientoProfesional("Dra.");var contact=new ActualizarUsuarioRequest();contact.setNroCelular("987654321");contact.setDireccion("Dirección profesional");r.setDatosUsuario(contact);vs.update(10L,r);
  assertEquals("Dirección profesional",us.obtener(1L).getDireccionContacto());assertEquals("QA-001",v.getNroColegiatura());assertEquals("Profesional",u.getNombres());assertEquals("12345678",u.getNroDocumento());assertEquals("hash conservado",u.getContrasena());
  rol("VETERINARIO");var self=vs.miPerfil(u.getCorreo());assertEquals("Dra.",self.tratamientoProfesional());assertEquals("Dirección profesional",self.direccion());
  assertThrows(AccessDeniedException.class,()->vs.update(10L,r));
 }
 @Test void contactoNoInterfiereConCambioDeContrasenaAutorizado(){
  var users=mock(UsuarioRepository.class);var encoder=new BCryptPasswordEncoder();var u=new UsuarioEntity();u.setIdUsuario(1L);u.setContrasena(encoder.encode("Clave@Sintetica37"));when(users.findById(1L)).thenReturn(Optional.of(u));
  var service=new CambioContrasenaService(users,encoder,mock(jakarta.persistence.EntityManager.class),java.time.Clock.systemUTC());
  service.cambiar(1L,new CambiarContrasenaRequest("Clave@Sintetica37","Consulta@Distinta48","Consulta@Distinta48"));
  assertTrue(encoder.matches("Consulta@Distinta48",u.getContrasena()));assertFalse(encoder.matches("Clave@Sintetica37",u.getContrasena()));verify(users).saveAndFlush(u);
 }
 @Test void colegiaturaProfesionalProtegidaYContratoContactoAceptado() throws Exception {
  var json=new ObjectMapper();
  assertThrows(Exception.class,()->json.readValue("{\"especialidad\":\"General\",\"nroColegiatura\":null}",UpdateVeterinarioRequest.class));
  assertThrows(Exception.class,()->json.readValue("{\"direccion\":\"\",\"correo\":null}",ActualizarDireccionPropietarioRequest.class));
  assertEquals("",json.readValue("{\"nroCelular\":\"\",\"direccion\":\"\"}",ContactoAdminRequest.class).direccion());
 }
 @Test void contactoUsaMismaFuenteEnUsuariosPropietariosYNoTocaIdentidad(){
  rol("ADMIN");var users=mock(UsuarioRepository.class);var owners=mock(PropietarioRepository.class);
  var role=new RolEntity();role.setNombre("PROPIETARIO");var u=new UsuarioEntity();u.setIdUsuario(1L);u.setRol(role);u.setNombres("Nombre");u.setApellidos("Apellido");u.setCorreo("qa@example.invalid");u.setTipoDocumento("DNI");u.setNroDocumento("12345678");
  var encoder=new BCryptPasswordEncoder();String hash=encoder.encode("Clave@Sintetica37");u.setContrasena(hash);
  var p=new PropietarioEntity();p.setIdPropietario(10L);p.setUsuario(u);p.setDireccion("Inicial");
  when(users.findById(1L)).thenReturn(Optional.of(u));when(owners.findByUsuarioIdUsuario(1L)).thenReturn(Optional.of(p));when(owners.findById(10L)).thenReturn(Optional.of(p));when(owners.saveAndFlush(any())).thenAnswer(c->c.getArgument(0));
  var um=Mappers.getMapper(UsuarioMapper.class);var us=new UsuarioServiceImpl(users,mock(RolRepository.class),um,encoder,owners,mock(VeterinarioRepository.class));
  var os=new PropietarioServiceImpl(owners,users,Mappers.getMapper(PropietarioMapper.class),um,mock(MascotaRepository.class));
  var r=new ActualizarUsuarioRequest();r.setNroCelular("987654321");r.setDireccion("Desde Usuarios");
  us.actualizar(1L,r);assertEquals("Desde Usuarios",os.obtener(10L).getDireccion());assertEquals("987654321",os.obtener(10L).getNroCelular());
  r.setDireccion("Desde Propietarios");os.actualizar(10L,r);assertEquals("Desde Propietarios",us.obtener(1L).getDireccionContacto());assertNull(u.getDireccionContacto());
  r.setNroCelular("");r.setDireccion("");us.actualizar(1L,r);assertEquals("",p.getDireccion());assertEquals("",u.getNroCelular());
  r.setNroCelular(null);r.setDireccion("Solo dirección");us.actualizar(1L,r);assertEquals("",u.getNroCelular());
  r.setNroCelular("incorrecto");r.setDireccion("No guardar");assertThrows(BadRequestException.class,()->os.actualizar(10L,r));assertEquals("Solo dirección",p.getDireccion());
  assertAll(()->assertEquals("Nombre",u.getNombres()),()->assertEquals("Apellido",u.getApellidos()),()->assertEquals("qa@example.invalid",u.getCorreo()),()->assertEquals("12345678",u.getNroDocumento()),()->assertEquals("DNI",u.getTipoDocumento()),()->assertSame(role,u.getRol()),()->assertEquals(hash,u.getContrasena()),()->assertTrue(encoder.matches("Clave@Sintetica37",u.getContrasena())));
  for(String other:List.of("PROPIETARIO","VETERINARIO")){rol(other);assertThrows(AccessDeniedException.class,()->us.actualizar(1L,r));assertThrows(AccessDeniedException.class,()->os.actualizar(10L,r));}
 }
}
