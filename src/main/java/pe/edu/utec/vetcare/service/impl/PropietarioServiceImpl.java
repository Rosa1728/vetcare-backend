package pe.edu.utec.vetcare.service.impl;

import pe.edu.utec.vetcare.mapper.UsuarioMapper;
import pe.edu.utec.vetcare.dto.response.UsuarioResponse;
import pe.edu.utec.vetcare.dto.request.ActualizarPerfilPropietarioRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.vetcare.dto.request.PropietarioRequest;
import pe.edu.utec.vetcare.dto.response.PropietarioResponse;
import pe.edu.utec.vetcare.entity.PropietarioEntity;
import pe.edu.utec.vetcare.entity.UsuarioEntity;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.mapper.PropietarioMapper;
import pe.edu.utec.vetcare.repository.PropietarioRepository;
import pe.edu.utec.vetcare.repository.UsuarioRepository;
import pe.edu.utec.vetcare.service.PropietarioService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PropietarioServiceImpl
        implements PropietarioService {

    private final PropietarioRepository propietarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PropietarioMapper propietarioMapper;
    private final UsuarioMapper usuarioMapper;
    private final pe.edu.utec.vetcare.repository.MascotaRepository mascotas;

    @Override
    @Transactional(readOnly = true)
    public List<PropietarioResponse> listar() {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();

        return propietarioRepository.findAll()
                .stream()
                .map(propietarioMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PropietarioResponse obtener(Long id) {

        PropietarioEntity entity = buscarPropietario(id);
        validarLecturaPropietario(entity);
        return propietarioMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PropietarioResponse obtenerMio() {
        return propietarioMapper.toResponse(propietarioAutenticado());
    }

    @Override
    @Transactional
    public PropietarioResponse crear(
            PropietarioRequest request
    ) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();

        if (propietarioRepository
                .existsByUsuarioIdUsuario(
                        request.getIdUsuario()
                )) {

            throw new BadRequestException(
                    "El usuario ya tiene un propietario asociado"
            );
        }

        UsuarioEntity usuario =
                usuarioRepository
                        .findById(request.getIdUsuario())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Usuario no encontrado"
                                )
                        );

        if (usuario.getRol()==null || !"PROPIETARIO".equals(usuario.getRol().getNombre()))
            throw new BadRequestException("Selecciona una cuenta de propietario");
        if (request.getNroCelular()!=null) usuario.setNroCelular(request.getNroCelular());
        PropietarioEntity entity =
                propietarioMapper.toEntity(request);

        entity.setUsuario(usuario);

        return propietarioMapper.toResponse(
                propietarioRepository.save(entity)
        );
    }

    @Override
    @Transactional
    public PropietarioResponse actualizar(Long id, PropietarioRequest request) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        var entity = buscarPropietario(id);
        if (!entity.getUsuario().getIdUsuario().equals(request.getIdUsuario()))
            throw new BadRequestException("La cuenta asociada al propietario no se puede cambiar");
        if (request.getNroCelular()!=null) {
            entity.getUsuario().setNroCelular(request.getNroCelular());
            usuarioRepository.save(entity.getUsuario());
        }
        propietarioMapper.updateEntity(request, entity);
        return propietarioMapper.toResponse(propietarioRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public PropietarioResponse actualizarMiDireccion(String direccion) {
        PropietarioEntity entity = propietarioAutenticado();
        entity.setDireccion(direccion);
        return propietarioMapper.toResponse(propietarioRepository.save(entity));
    }

    @Override
    @Transactional
    public UsuarioResponse actualizarMiPerfil(
            ActualizarPerfilPropietarioRequest request) {
        var propietario = propietarioAutenticado();
        var usuario = propietario.getUsuario();
        if (request.getNroCelular()==null && request.getDireccion()==null)
            throw new BadRequestException("Indica al menos un campo para actualizar");
        if (request.getNroCelular()!=null) usuario.setNroCelular(request.getNroCelular().trim());
        if (request.getDireccion()!=null) propietario.setDireccion(request.getDireccion());
        propietarioRepository.saveAndFlush(propietario);
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        if (mascotas.existsByPropietarioIdPropietario(id))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"El propietario tiene mascotas y sus registros deben conservarse");
        PropietarioEntity entity =
                buscarPropietario(id);

        propietarioRepository.delete(entity);
    }

    /**
     * ADMIN puede consultar cualquier propietario. PROPIETARIO solo el suyo.
     * El idUsuario proviene del JWT firmado, nunca de un dato del cliente.
     */
    private void validarLecturaPropietario(PropietarioEntity entity) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Acceso denegado");
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (admin) {
            return;
        }
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new AccessDeniedException("Acceso denegado");
        }
        Object claim = jwtAuth.getToken().getClaim("idUsuario");
        if (!(claim instanceof Number idUsuarioToken)
                || !entity.getUsuario().getIdUsuario().equals(idUsuarioToken.longValue())) {
            throw new AccessDeniedException("No puedes consultar otro propietario");
        }
    }

    private PropietarioEntity propietarioAutenticado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new AccessDeniedException("Acceso denegado");
        }
        Object claim = jwtAuth.getToken().getClaim("idUsuario");
        if (!(claim instanceof Number idUsuarioToken)) {
            throw new AccessDeniedException("Token sin identificador de usuario");
        }
        return propietarioRepository
                .findByUsuarioIdUsuario(idUsuarioToken.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("Propietario no encontrado"));
    }

    private PropietarioEntity buscarPropietario(
            Long id
    ) {

        return propietarioRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Propietario no encontrado"
                        )
                );
    }
}