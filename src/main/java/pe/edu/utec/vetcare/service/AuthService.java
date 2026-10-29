package pe.edu.utec.vetcare.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import pe.edu.utec.vetcare.dto.request.LoginRequest;
import pe.edu.utec.vetcare.dto.request.RegistroPropietarioRequest;
import pe.edu.utec.vetcare.dto.response.RegistroPropietarioResponse;
import pe.edu.utec.vetcare.entity.UsuarioEntity;
import pe.edu.utec.vetcare.entity.PropietarioEntity;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.repository.PropietarioRepository;
import pe.edu.utec.vetcare.repository.RolRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import pe.edu.utec.vetcare.dto.response.LoginResponse;
import pe.edu.utec.vetcare.repository.UsuarioRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final PropietarioRepository propietarios;
    private final RolRepository roles;
    private final pe.edu.utec.vetcare.security.CredencialJwt credencial;
    private static final long TOKEN_SEGUNDOS = 3600;

    @Transactional
    public RegistroPropietarioResponse registrarPropietario(RegistroPropietarioRequest request) {
        pe.edu.utec.vetcare.validation.PoliticaContrasena.validar(request.contrasena());
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        String documento = request.nroDocumento().trim();
        if (request.tipoDocumento().equals("DNI") ? !documento.matches("[0-9]{8}") : !documento.matches("[A-Za-z0-9]{6,20}"))
            throw new BadRequestException("Revisa el número de documento: DNI de 8 dígitos o documento de 6 a 20 letras y números");

        if (usuarios.existsByCorreo(correo)) {
            throw new BadRequestException("El correo ya se encuentra registrado");
        }
        if (usuarios.existsByNroDocumento(documento)) {
            throw new BadRequestException("El documento ya se encuentra registrado");
        }

        var rol = roles.findByNombre("PROPIETARIO")
                .orElseThrow(() -> new IllegalStateException("Falta configurar el rol PROPIETARIO"));

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNombres(request.nombres().trim());
        usuario.setApellidos(request.apellidos().trim());
        usuario.setTipoDocumento(request.tipoDocumento().trim());
        usuario.setNroDocumento(documento);
        usuario.setCorreo(correo);
        usuario.setContrasena(encoder.encode(request.contrasena()));
        usuario.setNroCelular(request.nroCelular());
        usuario.setRol(rol);
        usuario = usuarios.save(usuario);

        PropietarioEntity propietario = new PropietarioEntity();
        propietario.setUsuario(usuario);
        propietario.setDireccion(request.direccion());
        propietario = propietarios.save(propietario);

        return new RegistroPropietarioResponse(
                usuario.getIdUsuario(), propietario.getIdPropietario(), usuario.getCorreo(), "PROPIETARIO");
    }

    public LoginResponse login(LoginRequest request) {
        var usuario = usuarios.findByCorreo(request.correo()).orElse(null);
        if (usuario == null || !encoder.matches(request.contrasena(), usuario.getContrasena())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Credenciales invalidas");
        }
        String rol = usuario.getRol().getNombre();
        if (!java.util.List.of("ADMIN", "VETERINARIO", "PROPIETARIO").contains(rol)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Credenciales invalidas");
        }
        Instant ahora = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("vetcare")
                .subject(usuario.getCorreo())
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(TOKEN_SEGUNDOS))
                .claim("idUsuario", usuario.getIdUsuario())
                .claim("rol", rol)
                .claim("credencial", credencial.huella(usuario))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", TOKEN_SEGUNDOS);
    }
}
