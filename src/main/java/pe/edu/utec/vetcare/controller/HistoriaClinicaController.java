package pe.edu.utec.vetcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import pe.edu.utec.vetcare.dto.request.AntecedentesRequest;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.response.HistoriaClinicaResponse;
import pe.edu.utec.vetcare.service.HistoriaClinicaService;

@RestController
@RequestMapping(
        "/api/historias-clinicas"
)
@RequiredArgsConstructor
public class HistoriaClinicaController {

    private final HistoriaClinicaService service;

    @GetMapping("/{id}")
    public HistoriaClinicaResponse obtener(
            @PathVariable Long id
    ) {

        return service.obtener(id);
    }

    @GetMapping(
            "/mascota/{idMascota}"
    )
    public HistoriaClinicaResponse
    obtenerPorMascota(
            @PathVariable Long idMascota
    ) {

        return service.obtenerPorMascota(
                idMascota
        );
    }
    @GetMapping("/{id}/antecedentes/cambios")
    public pe.edu.utec.vetcare.dto.response.CambioAntecedenteResponse.Pagina cambios(@PathVariable("id") Long id,
            @RequestParam(value="seccion",required=false) String seccion,@RequestParam(value="pagina",defaultValue="0") int pagina,
            @RequestParam(value="size",defaultValue="20") int size){return service.cambiosAntecedentes(id,seccion,pagina,size);}
    @PutMapping("/{id}/antecedentes")
    @PreAuthorize("hasRole('VETERINARIO')")
    public HistoriaClinicaResponse actualizarAntecedentes(@PathVariable Long id,
            @Valid @RequestBody AntecedentesRequest request) {
        return service.actualizarAntecedentes(id, request);
    }
}
