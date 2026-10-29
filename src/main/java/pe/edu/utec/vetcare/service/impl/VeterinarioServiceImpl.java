package pe.edu.utec.vetcare.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.entity.RolEntity;
import pe.edu.utec.vetcare.repository.RolRepository;
import pe.edu.utec.vetcare.dto.request.RegistrarVeterinarioRequest;
import pe.edu.utec.vetcare.dto.request.ActualizarPerfilVeterinarioRequest;
import pe.edu.utec.vetcare.dto.response.PerfilVeterinarioResponse;
import pe.edu.utec.vetcare.dto.request.CreateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.request.UpdateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.response.VeterinarioResponse;
import pe.edu.utec.vetcare.entity.UsuarioEntity;
import pe.edu.utec.vetcare.entity.VeterinarioEntity;
import pe.edu.utec.vetcare.mapper.VeterinarioMapper;
import pe.edu.utec.vetcare.repository.UsuarioRepository;
import pe.edu.utec.vetcare.repository.VeterinarioRepository;
import pe.edu.utec.vetcare.service.VeterinarioService;

import java.util.List;

@Service
public class VeterinarioServiceImpl implements VeterinarioService {

    private final VeterinarioRepository veterinarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final VeterinarioMapper veterinarioMapper;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final pe.edu.utec.vetcare.service.UsuarioService usuarios;

    public VeterinarioServiceImpl(
            VeterinarioRepository veterinarioRepository,
            UsuarioRepository usuarioRepository,
            VeterinarioMapper veterinarioMapper,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder, pe.edu.utec.vetcare.service.UsuarioService usuarios) {

        this.veterinarioRepository = veterinarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.veterinarioMapper = veterinarioMapper;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VeterinarioResponse> getAll() {

        return veterinarioRepository.findAll()
                .stream()
                .map(veterinarioMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilVeterinarioResponse miPerfil(String correoAutenticado) {
        return aPerfil(buscarPorCorreoAutenticado(correoAutenticado));
    }

    @Override
    @Transactional
    public PerfilVeterinarioResponse actualizarMiPerfil(
            String correoAutenticado, ActualizarPerfilVeterinarioRequest request) {
        VeterinarioEntity veterinario = buscarPorCorreoAutenticado(correoAutenticado);
        UsuarioEntity usuario = veterinario.getUsuario();

        if (request.getNroCelular() == null && request.getDireccion() == null) {
            throw new BadRequestException("Debe indicar al menos un campo para actualizar");
        }
        if (request.getNroCelular() != null) {
            if (request.getNroCelular().isBlank()) {
                throw new BadRequestException("El celular no puede estar vacío");
            }
            usuario.setNroCelular(request.getNroCelular().trim());
        }
        if (request.getDireccion() != null) {
            usuario.setDireccionContacto(request.getDireccion().trim());
        }
        usuarioRepository.save(usuario);
        return aPerfil(veterinario);
    }

    private VeterinarioEntity buscarPorCorreoAutenticado(String correo) {
        UsuarioEntity usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
        return veterinarioRepository.findByUsuario_IdUsuario(usuario.getIdUsuario())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil veterinario no encontrado"));
    }

    private PerfilVeterinarioResponse aPerfil(VeterinarioEntity veterinario) {
        UsuarioEntity usuario = veterinario.getUsuario();
        return new PerfilVeterinarioResponse(
                veterinario.getIdVeterinario(), usuario.getIdUsuario(),
                usuario.getNombres(), usuario.getApellidos(), usuario.getCorreo(),
                usuario.getNroCelular(), veterinario.getEspecialidad(),
                veterinario.getNroColegiatura(), veterinario.getTratamientoProfesional(),
                usuario.getDireccionContacto());
    }

    @Override
    public VeterinarioResponse getById(Long id) {

        VeterinarioEntity entity =
                veterinarioRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Veterinario no encontrado"
                                )
                        );

        return veterinarioMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public VeterinarioResponse create(
            CreateVeterinarioRequest request) {

        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        UsuarioEntity usuario =
                usuarioRepository.findById(
                                request.getIdUsuario()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Usuario no encontrado"
                                )
                        );

        validarUsuarioVeterinario(usuario);
        validarNoAsociado(usuario.getIdUsuario());
        validarColegiatura(request.getNroColegiatura());

        VeterinarioEntity entity =
                veterinarioMapper.toEntity(request);

        entity.setUsuario(usuario);
        entity.setTratamientoProfesional(normalizarTratamiento(request.getTratamientoProfesional()));

        VeterinarioEntity savedEntity =
                veterinarioRepository.save(entity);

        return veterinarioMapper.toResponse(savedEntity);
    }

    @Override
    @Transactional
    public VeterinarioResponse update(
            Long id,
            UpdateVeterinarioRequest request) {

        VeterinarioEntity entity =
                veterinarioRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Veterinario no encontrado"
                                )
                        );

        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        if (!entity.getNroColegiatura().equals(request.getNroColegiatura())
                && veterinarioRepository.existsByNroColegiatura(request.getNroColegiatura())) {
            throw new BadRequestException("El número de colegiatura ya está registrado");
        }
        if (request.getDatosUsuario() != null) usuarios.actualizar(entity.getUsuario().getIdUsuario(), request.getDatosUsuario());
        veterinarioMapper.updateEntity(request, entity);
        if (request.getTratamientoProfesional() != null) entity.setTratamientoProfesional(normalizarTratamiento(request.getTratamientoProfesional()));

        VeterinarioEntity updatedEntity =
                veterinarioRepository.save(entity);

        return veterinarioMapper.toResponse(updatedEntity);
    }


    @Override
    @Transactional
    public VeterinarioResponse registrar(RegistrarVeterinarioRequest request) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        pe.edu.utec.vetcare.validation.PoliticaContrasena.validar(request.getContrasena());
        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BadRequestException("El correo ya se encuentra registrado");
        }
        if (usuarioRepository.existsByNroDocumento(request.getNroDocumento())) {
            throw new BadRequestException("El documento ya se encuentra registrado");
        }
        validarColegiatura(request.getNroColegiatura());
        RolEntity rol = rolRepository.findByNombre("VETERINARIO")
                .orElseThrow(() -> new ResourceNotFoundException("Rol VETERINARIO no encontrado"));
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNombres(request.getNombres());
        usuario.setApellidos(request.getApellidos());
        usuario.setTipoDocumento(request.getTipoDocumento());
        usuario.setNroDocumento(request.getNroDocumento());
        usuario.setCorreo(request.getCorreo());
        usuario.setNroCelular(request.getNroCelular());
        usuario.setContrasena(passwordEncoder.encode(request.getContrasena()));
        usuario.setRol(rol);
        usuario = usuarioRepository.save(usuario);

        VeterinarioEntity veterinario = new VeterinarioEntity();
        veterinario.setUsuario(usuario);
        veterinario.setTratamientoProfesional(normalizarTratamiento(request.getTratamientoProfesional()));
        veterinario.setEspecialidad(request.getEspecialidad());
        veterinario.setNroColegiatura(request.getNroColegiatura());
        return veterinarioMapper.toResponse(veterinarioRepository.save(veterinario));
    }

    private String normalizarTratamiento(String value) { return value == null || value.isBlank() ? null : value; }

    private void validarUsuarioVeterinario(UsuarioEntity usuario) {
        if (usuario.getRol() == null || !"VETERINARIO".equals(usuario.getRol().getNombre())) {
            throw new BadRequestException("El usuario debe tener rol VETERINARIO");
        }
    }

    private void validarNoAsociado(Long idUsuario) {
        if (veterinarioRepository.existsByUsuario_IdUsuario(idUsuario)) {
            throw new BadRequestException("El usuario ya tiene un perfil veterinario");
        }
    }

    private void validarColegiatura(String colegiatura) {
        if (veterinarioRepository.existsByNroColegiatura(colegiatura)) {
            throw new BadRequestException("El número de colegiatura ya está registrado");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {

        VeterinarioEntity entity =
                veterinarioRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Veterinario no encontrado"
                                )
                        );

        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        veterinarioRepository.delete(entity);
    }
}