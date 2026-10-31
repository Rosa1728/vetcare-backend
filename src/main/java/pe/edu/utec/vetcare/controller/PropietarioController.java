package pe.edu.utec.vetcare.controller;

import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.dto.response.UsuarioResponse;
import pe.edu.utec.vetcare.dto.request.ActualizarPerfilPropietarioRequest;
import java.util.Set;
import java.util.Map;
import jakarta.validation.Validator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.PropietarioRequest;
import pe.edu.utec.vetcare.dto.request.ActualizarUsuarioRequest;
import pe.edu.utec.vetcare.dto.request.ActualizarDireccionPropietarioRequest;
import pe.edu.utec.vetcare.dto.response.PropietarioResponse;
import pe.edu.utec.vetcare.service.PropietarioService;

import java.util.List;

@RestController
@RequestMapping("/api/propietarios")
@RequiredArgsConstructor
public class PropietarioController {

    private final PropietarioService propietarioService;
    private final Validator validator;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<PropietarioResponse> listar() {

        return propietarioService.listar();
    }

    @PreAuthorize("hasRole('PROPIETARIO')")
    @GetMapping("/me")
    public PropietarioResponse miPerfil() {
        return propietarioService.obtenerMio();
    }

    @GetMapping("/{id}")
    public PropietarioResponse obtener(
            @PathVariable Long id
    ) {

        return propietarioService.obtener(id);
    }

    @PreAuthorize("hasRole('PROPIETARIO')")
    @PatchMapping("/me")
    public UsuarioResponse actualizarMiPerfil(
            @RequestBody Map<String, Object> campos) {
        var request = new ActualizarPerfilPropietarioRequest();
        for (var entry : campos.entrySet()) {
            if (!Set.of("nroCelular", "direccion").contains(entry.getKey()))
                throw new BadRequestException("Campo no permitido: " + entry.getKey());
            if (!(entry.getValue() instanceof String value))
                throw new BadRequestException("El campo debe ser texto: " + entry.getKey());
            switch(entry.getKey()) {
                case "nroCelular" -> request.setNroCelular(value);
                case "direccion" -> request.setDireccion(value);
            }
        }
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new BadRequestException(errors.iterator().next().getMessage());
        return propietarioService.actualizarMiPerfil(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropietarioResponse crear(
            @Valid
            @RequestBody PropietarioRequest request
    ) {

        return propietarioService.crear(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public PropietarioResponse actualizar(
            @PathVariable Long id,
            @Valid
            @RequestBody ActualizarUsuarioRequest request
    ) {

        return propietarioService.actualizar(
                id,
                request
        );
    }

    @PreAuthorize("hasRole('PROPIETARIO')")
    @PatchMapping("/me/direccion")
    public PropietarioResponse actualizarMiDireccion(
            @Valid @RequestBody ActualizarDireccionPropietarioRequest request
    ) {
        return propietarioService.actualizarMiDireccion(request.getDireccion());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable Long id
    ) {

        propietarioService.eliminar(id);
    }
}