package pe.edu.utec.vetcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.vetcare.service.FotoPerfilService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.UsuarioRequest;
import pe.edu.utec.vetcare.dto.request.ActualizarUsuarioRequest;
import pe.edu.utec.vetcare.dto.response.UsuarioResponse;
import pe.edu.utec.vetcare.service.UsuarioService;
import pe.edu.utec.vetcare.repository.UsuarioRepository;
import pe.edu.utec.vetcare.mapper.UsuarioMapper;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final pe.edu.utec.vetcare.repository.RolRepository roles;
    private final FotoPerfilService fotoPerfilService;
    private final pe.edu.utec.vetcare.repository.PropietarioRepository propietarios;
    private final pe.edu.utec.vetcare.service.CambioContrasenaService cambioContrasena;

    // Cada usuario autenticado consulta únicamente su propio perfil.
    @GetMapping("/me")
    public UsuarioResponse miPerfil(@AuthenticationPrincipal Jwt jwt) {
        // No se acepta un ID del cliente: el usuario se obtiene del JWT validado.
        Long idUsuario = jwt.getClaim("idUsuario");
        if (idUsuario == null) {
            throw new ResourceNotFoundException("Usuario autenticado no encontrado");
        }
        var usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));
        var response = usuarioMapper.toResponse(usuario);
        if (usuario.getRol()!=null && "PROPIETARIO".equals(usuario.getRol().getNombre()))
            propietarios.findByUsuarioIdUsuario(idUsuario).ifPresent(p -> response.setDireccionContacto(p.getDireccion()));
        if (usuario.getFotoPerfil() != null) {
            response.setFotoPerfilUrl("/api/usuarios/me/foto");
        }
        return response;
    }

    @PostMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UsuarioResponse subirMiFoto(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam("foto") MultipartFile foto) {
        fotoPerfilService.guardar(idActual(jwt), foto);
        return miPerfil(jwt);
    }

    @GetMapping("/me/foto")
    public ResponseEntity<byte[]> consultarMiFoto(@AuthenticationPrincipal Jwt jwt) {
        var foto = fotoPerfilService.leer(idActual(jwt));
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).contentType(foto.tipo()).body(foto.contenido());
    }

    @DeleteMapping("/me/foto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarMiFoto(@AuthenticationPrincipal Jwt jwt) {
        fotoPerfilService.eliminar(idActual(jwt));
    }

    private Long idActual(Jwt jwt) {
        Long id = jwt.getClaim("idUsuario");
        if (id == null) throw new ResourceNotFoundException("Usuario autenticado no encontrado");
        return id;
    }

    @PostMapping("/me/contrasena")
    public java.util.Map<String,String> cambiarMiContrasena(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody pe.edu.utec.vetcare.dto.request.CambiarContrasenaRequest request) {
        cambioContrasena.cambiar(idActual(jwt), request);
        return java.util.Map.of("mensaje", "Contraseña actualizada. Inicia sesión con tu nueva contraseña.");
    }

    @PatchMapping("/me")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public UsuarioResponse actualizarContacto(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody pe.edu.utec.vetcare.dto.request.ContactoAdminRequest request) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        pe.edu.utec.vetcare.validation.ContactoEdicion.validar(request.nroCelular(),request.direccion(),true);
        var u = usuarioRepository.findById(idActual(jwt)).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (request.nroCelular() == null && request.direccion() == null) throw new pe.edu.utec.vetcare.exception.BadRequestException("Indica celular o dirección");
        if (request.nroCelular() != null) u.setNroCelular(request.nroCelular().trim());
        if (request.direccion() != null) u.setDireccionContacto(request.direccion().trim());
        usuarioRepository.saveAndFlush(u);
        return usuarioMapper.toResponse(u);
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> fotoAdministrada(@PathVariable Long id) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin();
        var foto = fotoPerfilService.leer(id);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).contentType(foto.tipo()).body(foto.contenido());
    }
    @PostMapping(value="/{id}/foto", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.transaction.annotation.Transactional
    public void guardarFotoAdministrada(@PathVariable Long id, @RequestParam("foto") MultipartFile foto) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin(); comprobarFotoAdministrable(id); fotoPerfilService.guardar(id,foto);
    }
    @DeleteMapping("/{id}/foto")
    @org.springframework.transaction.annotation.Transactional
    public void borrarFotoAdministrada(@PathVariable Long id) {
        pe.edu.utec.vetcare.security.AccesoClinico.soloAdmin(); comprobarFotoAdministrable(id); fotoPerfilService.eliminar(id);
    }

    private void comprobarFotoAdministrable(Long id) {
        var usuario = usuarioRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (usuario.getRol() != null && "VETERINARIO".equals(usuario.getRol().getNombre())) {
            throw new org.springframework.security.access.AccessDeniedException("La fotografía debe gestionarla el veterinario desde Mi perfil");
        }
    }

    @GetMapping("/roles")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public java.util.List<java.util.Map<String,Object>> rolesDisponibles() {
        return roles.findAll().stream().filter(r -> java.util.List.of("ADMIN","VETERINARIO","PROPIETARIO").contains(r.getNombre()))
                .map(r -> java.util.Map.<String,Object>of("idRol",r.getIdRol(),"nombre",r.getNombre())).toList();
    }

    @GetMapping
    public List<UsuarioResponse> listar() {

        return usuarioService.listar();
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(
            @PathVariable Long id
    ) {

        return usuarioService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(
            @Valid
            @RequestBody UsuarioRequest request
    ) {

        return usuarioService.crear(request);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(
            @PathVariable Long id,
            @Valid
            @RequestBody ActualizarUsuarioRequest request
    ) {

        return usuarioService.actualizar(
                id,
                request
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable Long id
    ) {

        usuarioService.eliminar(id);
    }
}