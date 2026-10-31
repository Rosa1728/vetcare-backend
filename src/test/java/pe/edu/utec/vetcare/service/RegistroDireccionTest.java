package pe.edu.utec.vetcare.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pe.edu.utec.vetcare.dto.request.ActualizarPerfilPropietarioRequest;
import pe.edu.utec.vetcare.dto.request.RegistroPropietarioRequest;
import pe.edu.utec.vetcare.entity.PropietarioEntity;
import pe.edu.utec.vetcare.entity.RolEntity;
import pe.edu.utec.vetcare.entity.UsuarioEntity;
import pe.edu.utec.vetcare.mapper.PropietarioMapper;
import pe.edu.utec.vetcare.mapper.UsuarioMapper;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.security.CredencialJwt;
import pe.edu.utec.vetcare.service.impl.PropietarioServiceImpl;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Contrato y servicios reales con repositorios simulados: no inicia Spring ni usa una BD. */
class RegistroDireccionTest {
    private static final String PASSWORD = "Consulta@Segura37";
    @AfterEach void limpiarContexto() { SecurityContextHolder.clearContext(); }

    private RegistroPropietarioRequest request(String addressJson) throws Exception {
        String suffix = addressJson.isEmpty() ? "" : ",\"direccion\":" + addressJson;
        return new ObjectMapper().readValue("""
                {"nombres":"Persona","apellidos":"Sintética","tipoDocumento":"DNI",
                 "nroDocumento":"12345678","correo":"qa@example.invalid",
                 "contrasena":"Consulta@Segura37"%s}
                """.formatted(suffix), RegistroPropietarioRequest.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "\"\"", "\"Av. Perú 125\""})
    void registroAdmiteDireccionOpcionalYPerfilPuedeAgregarlaOEditarDespues(String addressJson) throws Exception {
        var request = request(addressJson);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
        var users = mock(UsuarioRepository.class);
        var owners = mock(PropietarioRepository.class);
        var roles = mock(RolRepository.class);
        var encoder = new BCryptPasswordEncoder();
        var role = new RolEntity(); role.setIdRol(2L); role.setNombre("PROPIETARIO");
        when(roles.findByNombre("PROPIETARIO")).thenReturn(Optional.of(role));
        when(users.save(any(UsuarioEntity.class))).thenAnswer(call -> {
            UsuarioEntity user = call.getArgument(0); user.setIdUsuario(1L); return user;
        });
        when(owners.save(any(PropietarioEntity.class))).thenAnswer(call -> {
            PropietarioEntity owner = call.getArgument(0); owner.setIdPropietario(10L); return owner;
        });
        var auth = new AuthService(users, encoder, mock(JwtEncoder.class), owners, roles, mock(CredencialJwt.class));
        assertEquals("PROPIETARIO", auth.registrarPropietario(request).rol());
        var capture = org.mockito.ArgumentCaptor.forClass(PropietarioEntity.class);
        verify(owners).save(capture.capture());
        var owner = capture.getValue();
        assertEquals(request.direccion(), owner.getDireccion());
        String hash = owner.getUsuario().getContrasena();
        assertTrue(encoder.matches(PASSWORD, hash));

        when(owners.findByUsuarioIdUsuario(1L)).thenReturn(Optional.of(owner));
        var jwt = Jwt.withTokenValue("unit-only").header("alg", "none")
                .claim("idUsuario", 1L).subject("qa@example.invalid").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_PROPIETARIO"))));
        var profiles = new PropietarioServiceImpl(owners, users, Mappers.getMapper(PropietarioMapper.class),
                Mappers.getMapper(UsuarioMapper.class), mock(MascotaRepository.class));
        var update = new ActualizarPerfilPropietarioRequest();
        update.setDireccion("Dirección posterior 456");
        profiles.actualizarMiPerfil(update);
        verify(owners).saveAndFlush(owner);
        assertEquals("Dirección posterior 456", profiles.obtenerMio().getDireccion());
        assertEquals(hash, owner.getUsuario().getContrasena());
        assertEquals("qa@example.invalid", owner.getUsuario().getCorreo());
        assertEquals("12345678", owner.getUsuario().getNroDocumento());
        assertEquals(role, owner.getUsuario().getRol());
    }

    @Test void direccionConservaLimiteDe200CaracteresEnRegistroYPerfil() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request("\"" + "a".repeat(200) + "\"")).isEmpty());
            assertTrue(validator.validate(request("\"" + "a".repeat(201) + "\"")).stream()
                    .anyMatch(error -> error.getPropertyPath().toString().equals("direccion")));
            var update = new ActualizarPerfilPropietarioRequest(); update.setDireccion("a".repeat(201));
            assertFalse(validator.validate(update).isEmpty());
        }
    }
}
