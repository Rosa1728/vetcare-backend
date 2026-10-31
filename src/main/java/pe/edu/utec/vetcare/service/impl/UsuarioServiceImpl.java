package pe.edu.utec.vetcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.vetcare.dto.request.UsuarioRequest;
import pe.edu.utec.vetcare.dto.request.ActualizarUsuarioRequest;
import pe.edu.utec.vetcare.dto.response.UsuarioResponse;
import pe.edu.utec.vetcare.entity.RolEntity;
import pe.edu.utec.vetcare.entity.UsuarioEntity;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.mapper.UsuarioMapper;
import pe.edu.utec.vetcare.repository.RolRepository;
import pe.edu.utec.vetcare.repository.UsuarioRepository;
import pe.edu.utec.vetcare.service.UsuarioService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl
        implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final pe.edu.utec.vetcare.repository.PropietarioRepository propietarios;
    private final pe.edu.utec.vetcare.repository.VeterinarioRepository veterinarios;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {

        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        return usuarioRepository.findAll()
                .stream()
                .map(this::respuestaConContacto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();

        var entity = buscarUsuario(id);
        return respuestaConContacto(entity);
    }

    @Override
    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();

        pe.edu.utec.vetcare.validation.PoliticaContrasena.validar(request.getContrasena());
        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BadRequestException("El correo ya se encuentra registrado");
        }

        if (usuarioRepository.existsByNroDocumento(request.getNroDocumento())) {
            throw new BadRequestException("El documento ya se encuentra registrado");
        }

        RolEntity rol = rolRepository
                .findById(request.getIdRol())
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));

        if (!java.util.List.of("ADMIN","VETERINARIO","PROPIETARIO").contains(rol.getNombre())) throw new BadRequestException("Selecciona un rol válido");
        if ("ADMIN".equals(rol.getNombre()) && !Boolean.TRUE.equals(request.getConfirmarAdmin())) throw new BadRequestException("Confirma los privilegios de la nueva cuenta ADMIN antes de crearla");
        UsuarioEntity entity = usuarioMapper.toEntity(request);

        entity.setRol(rol);

        entity.setContrasena(passwordEncoder.encode(request.getContrasena()));

        return usuarioMapper.toResponse(usuarioRepository.save(entity));
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        UsuarioEntity entity = buscarUsuario(id);
        var perfil = "PROPIETARIO".equals(entity.getRol().getNombre()) ? propietarios.findByUsuarioIdUsuario(id) : java.util.Optional.<pe.edu.utec.vetcare.entity.PropietarioEntity>empty();
        pe.edu.utec.vetcare.validation.ContactoEdicion.validar(request.getNroCelular(),request.getDireccion(),perfil.isPresent());
        if(request.getNroCelular()!=null)entity.setNroCelular(request.getNroCelular());
        if(request.getDireccion()!=null) {
            if(perfil.isPresent()) { perfil.get().setDireccion(request.getDireccion());propietarios.saveAndFlush(perfil.get()); }
            else entity.setDireccionContacto(request.getDireccion());
        }
        usuarioRepository.saveAndFlush(entity);
        return respuestaConContacto(entity);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        // Serializa las eliminaciones para comprobar el último ADMIN sin migraciones.
        rolRepository.bloquearAdministracion().orElseThrow(() -> new ResourceNotFoundException("Rol ADMIN no encontrado"));

        UsuarioEntity entity = buscarUsuario(id);

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            Object actual = jwt.getToken().getClaim("idUsuario");
            if (actual instanceof Number idActual && entity.getIdUsuario().equals(idActual.longValue()))
                throw new BadRequestException("No puedes eliminar la cuenta con la que iniciaste sesión");
        }
        if ("ADMIN".equals(entity.getRol().getNombre()) && usuarioRepository.countByRol_Nombre("ADMIN")<=1)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"No se puede eliminar al último administrador");
        if (veterinarios.existsByUsuario_IdUsuario(id) || propietarios.findByUsuarioIdUsuario(id).isPresent())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"La cuenta tiene un perfil asociado que debe conservarse");
        usuarioRepository.delete(entity);
    }

    private UsuarioResponse respuestaConContacto(UsuarioEntity entity) {
        var response=usuarioMapper.toResponse(entity);
        if(entity.getRol()!=null&&"PROPIETARIO".equals(entity.getRol().getNombre()))
            propietarios.findByUsuarioIdUsuario(entity.getIdUsuario()).ifPresent(p->response.setDireccionContacto(p.getDireccion()));
        return response;
    }

    private UsuarioEntity buscarUsuario(Long id) {

        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}