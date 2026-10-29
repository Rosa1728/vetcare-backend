package pe.edu.utec.vetcare.controller;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import pe.edu.utec.vetcare.exception.BadRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import pe.edu.utec.vetcare.dto.request.ActualizarPerfilVeterinarioRequest;
import pe.edu.utec.vetcare.dto.response.PerfilVeterinarioResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.CreateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.request.RegistrarVeterinarioRequest;
import pe.edu.utec.vetcare.dto.request.UpdateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.response.VeterinarioResponse;
import pe.edu.utec.vetcare.service.VeterinarioService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/veterinarios")
public class VeterinarioController {

    private final VeterinarioService veterinarioService;
    private final Validator validator;
    private final pe.edu.utec.vetcare.service.FotoPerfilService fotos;

    public VeterinarioController(VeterinarioService veterinarioService,
                                 Validator validator, pe.edu.utec.vetcare.service.FotoPerfilService fotos) {
        this.veterinarioService = veterinarioService;
        this.validator = validator;
        this.fotos = fotos;
    }

    @GetMapping
    public List<VeterinarioResponse> getAll() {
        return veterinarioService.getAll();
    }

    @PreAuthorize("hasRole('VETERINARIO')")
    @GetMapping("/me")
    public PerfilVeterinarioResponse miPerfil(@AuthenticationPrincipal Jwt jwt) {
        return veterinarioService.miPerfil(jwt.getSubject());
    }

    @PreAuthorize("hasRole('VETERINARIO')")
    @PatchMapping("/me")
    public ResponseEntity<?> actualizarMiPerfil(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> campos) {

        Set<String> permitidos = Set.of("nroCelular", "direccion");
        for (String campo : campos.keySet()) {
            if (!permitidos.contains(campo)) {
                throw new BadRequestException("Campo no permitido: " + campo);
            }
        }

        ActualizarPerfilVeterinarioRequest request = new ActualizarPerfilVeterinarioRequest();
        request.setNroCelular(leerTexto(campos, "nroCelular"));
        request.setDireccion(leerTexto(campos, "direccion"));

        var errores = validator.validate(request);
        if (!errores.isEmpty()) {
            throw new BadRequestException(errores.iterator().next().getMessage());
        }

        return ResponseEntity.ok(veterinarioService.actualizarMiPerfil(jwt.getSubject(), request));
    }

    private String leerTexto(Map<String, Object> campos, String nombre) {
        if (!campos.containsKey(nombre)) {
            return null;
        }
        Object valor = campos.get(nombre);
        if (!(valor instanceof String texto)) {
            throw new BadRequestException("El campo " + nombre + " debe ser texto");
        }
        return texto;
    }

    // La lectura solo resuelve perfiles veterinarios, nunca IDs arbitrarios de usuarios.
    @GetMapping("/{id}/foto")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<byte[]> fotografia(@PathVariable Long id) {
        var perfil = veterinarioService.getById(id);
        var foto = fotos.leer(perfil.getIdUsuario());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(foto.tipo()).body(foto.contenido());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VeterinarioResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(veterinarioService.getById(id));
    }

    @PostMapping("/registro")
    public ResponseEntity<VeterinarioResponse> registrar(@Valid @RequestBody RegistrarVeterinarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(veterinarioService.registrar(request));
    }

    @PostMapping
    public ResponseEntity<VeterinarioResponse> create(@Valid @RequestBody CreateVeterinarioRequest request) {
        VeterinarioResponse veterinarioCreated = veterinarioService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(veterinarioCreated);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VeterinarioResponse> update(
            @PathVariable Long id,
            @Valid
            @RequestBody
            UpdateVeterinarioRequest request) {

        VeterinarioResponse veterinarioUpdated = veterinarioService.update(id, request);
        return ResponseEntity.ok(veterinarioUpdated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        veterinarioService.delete(id);
        return ResponseEntity.noContent().build();
    }
}