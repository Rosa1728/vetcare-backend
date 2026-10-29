package pe.edu.utec.vetcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.MascotaRequest;
import pe.edu.utec.vetcare.dto.response.MascotaResponse;
import pe.edu.utec.vetcare.service.MascotaService;

import java.util.List;

@RestController
@RequestMapping("/api/mascotas")
@RequiredArgsConstructor
public class MascotaController {

    private final MascotaService mascotaService;

    @GetMapping
    public List<MascotaResponse> listar() {

        return mascotaService.listar();
    }

    @GetMapping("/{id}")
    public MascotaResponse obtener(
            @PathVariable Long id
    ) {

        return mascotaService.obtener(id);
    }

    @GetMapping(
            "/propietario/{idPropietario}"
    )
    public List<MascotaResponse>
    listarPorPropietario(
            @PathVariable Long idPropietario
    ) {

        return mascotaService
                .listarPorPropietario(
                        idPropietario
                );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MascotaResponse crear(
            @Valid
            @RequestBody MascotaRequest request
    ) {

        return mascotaService.crear(request);
    }

    @PutMapping("/{id}")
    public MascotaResponse actualizar(
            @PathVariable Long id,
            @Valid
            @RequestBody MascotaRequest request
    ) {

        return mascotaService.actualizar(
                id,
                request
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(
            @PathVariable Long id
    ) {

        mascotaService.eliminar(id);
    }
}