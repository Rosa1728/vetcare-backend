package pe.edu.utec.vetcare.service;

import java.util.List;
import java.util.Optional;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import pe.edu.utec.vetcare.controller.VeterinarioController;
import pe.edu.utec.vetcare.dto.request.*;
import pe.edu.utec.vetcare.entity.*;
import pe.edu.utec.vetcare.mapper.VeterinarioMapper;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.security.CredencialJwt;
import pe.edu.utec.vetcare.service.impl.VeterinarioServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Servicios y BCrypt reales con repositorios simulados; no conecta a bases de datos. */
class VeterinarioRegistroTest {
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }
    private void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "qa@example.invalid", "unused", List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    private RegistrarVeterinarioRequest request(String treatment) {
        var r=new RegistrarVeterinarioRequest();
        r.setNombres("Persona");r.setApellidos("Sintética");r.setTipoDocumento("DNI");
        r.setNroDocumento("12345678");r.setCorreo("vet-qa@example.invalid");
        r.setContrasena("Consulta@Segura37");r.setNroCelular("987654321");
        r.setTratamientoProfesional(treatment);r.setEspecialidad("General");r.setNroColegiatura("QA-12345");
        return r;
    }
    @ParameterizedTest @ValueSource(strings={"Dr.","Dra.",""})
    void registraListaEditaTratamientoYLoginValidaCredencialSinPerderIdentidad(String treatment) {
        role("ADMIN");var users=mock(UsuarioRepository.class);var vets=mock(VeterinarioRepository.class);
        var roles=mock(RolRepository.class);var encoder=new BCryptPasswordEncoder();var userService=mock(UsuarioService.class);
        var role=new RolEntity();role.setIdRol(3L);role.setNombre("VETERINARIO");
        when(roles.findByNombre("VETERINARIO")).thenReturn(Optional.of(role));
        when(users.save(any(UsuarioEntity.class))).thenAnswer(call->{UsuarioEntity u=call.getArgument(0);u.setIdUsuario(1L);return u;});
        when(vets.save(any(VeterinarioEntity.class))).thenAnswer(call->{VeterinarioEntity v=call.getArgument(0);v.setIdVeterinario(10L);return v;});
        var service=new VeterinarioServiceImpl(vets,users,Mappers.getMapper(VeterinarioMapper.class),roles,encoder,userService);
        var request=request(treatment);var response=service.registrar(request);
        assertEquals("Persona",response.getNombres());assertEquals("Sintética",response.getApellidos());
        assertEquals(treatment.isEmpty()?null:treatment,response.getTratamientoProfesional());
        var capture=ArgumentCaptor.forClass(VeterinarioEntity.class);verify(vets).save(capture.capture());var saved=capture.getValue();
        String hash=saved.getUsuario().getContrasena();assertNotEquals(request.getContrasena(),hash);assertTrue(encoder.matches(request.getContrasena(),hash));
        when(vets.findAll()).thenReturn(List.of(saved));assertEquals("Persona",service.getAll().getFirst().getNombres());
        when(vets.findById(10L)).thenReturn(Optional.of(saved));
        var update=new UpdateVeterinarioRequest();update.setEspecialidad("General");update.setTratamientoProfesional("Dra.");
        assertEquals("Dra.",service.update(10L,update).getTratamientoProfesional());assertEquals(hash,saved.getUsuario().getContrasena());
        var jwtEncoder=mock(JwtEncoder.class);when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("unit-only").header("alg","HS256").subject(request.getCorreo()).build());
        var credential=mock(CredencialJwt.class);when(credential.huella(saved.getUsuario())).thenReturn("unit-only");
        when(users.findByCorreo(request.getCorreo())).thenReturn(Optional.of(saved.getUsuario()));
        var auth=new AuthService(users,encoder,jwtEncoder,mock(PropietarioRepository.class),roles,credential);
        var login=new LoginRequest(request.getCorreo(),request.getContrasena());auth.login(login);
        var params=ArgumentCaptor.forClass(JwtEncoderParameters.class);verify(jwtEncoder).encode(params.capture());
        assertEquals("VETERINARIO",params.getValue().getClaims().getClaims().get("rol"));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->auth.login(new LoginRequest(request.getCorreo(),"Incorrecta!12345")));
    }
    @Test void registroSigueRestringidoAAdmin() {
        role("PROPIETARIO");var users=mock(UsuarioRepository.class);var vets=mock(VeterinarioRepository.class);
        var service=new VeterinarioServiceImpl(vets,users,Mappers.getMapper(VeterinarioMapper.class),mock(RolRepository.class),new BCryptPasswordEncoder(),mock(UsuarioService.class));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.registrar(request("Dr.")));
        verifyNoInteractions(users,vets);
    }
    @Test void perfilPropioNoPermiteEditarTratamiento() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var service=mock(VeterinarioService.class);
            var controller=new VeterinarioController(service,factory.getValidator(),mock(FotoPerfilService.class));
            var jwt=Jwt.withTokenValue("unit-only").header("alg","none").subject("vet-qa@example.invalid").build();
            assertThrows(pe.edu.utec.vetcare.exception.BadRequestException.class,()->controller.actualizarMiPerfil(jwt,java.util.Map.of("tratamientoProfesional","Dr.")));
            verifyNoInteractions(service);
        }
    }
}
