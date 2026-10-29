package pe.edu.utec.vetcare.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.CreateCitaRequest;
import pe.edu.utec.vetcare.dto.request.UpdateCitaRequest;
import pe.edu.utec.vetcare.dto.response.CitaResponse;
import pe.edu.utec.vetcare.service.CitaService;

import java.util.List;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping("/disponibilidad")
    public ResponseEntity<pe.edu.utec.vetcare.dto.response.DisponibilidadCitaResponse> disponibilidad(
            @RequestParam("idVeterinario") Long idVeterinario,@RequestParam("fecha") @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fecha,
            @RequestParam(value="excluirCita",required=false) Long excluirCita) {
        return ResponseEntity.ok(citaService.disponibilidad(idVeterinario,fecha,excluirCita));
    }

    @GetMapping
    public ResponseEntity<List<CitaResponse>> getAll() {
        return ResponseEntity.ok(citaService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CitaResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(citaService.getById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROPIETARIO')")
    @PostMapping
    public ResponseEntity<CitaResponse> create(@Valid @RequestBody CreateCitaRequest request) {
        return ResponseEntity.ok(citaService.create(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROPIETARIO')")
    @PutMapping("/{id}")
    public ResponseEntity<CitaResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateCitaRequest request) {
        return ResponseEntity.ok(citaService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        citaService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'VETERINARIO')")
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<CitaResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(citaService.confirm(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROPIETARIO', 'VETERINARIO')")
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<CitaResponse> cancel(@PathVariable Long id, @Valid @RequestBody pe.edu.utec.vetcare.dto.request.CancelarCitaRequest request) {
        return ResponseEntity.ok(citaService.cancel(id, request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'VETERINARIO')")
    @PutMapping("/{id}/atender")
    public ResponseEntity<CitaResponse> attend(@PathVariable Long id) {
        return ResponseEntity.ok(citaService.attend(id));
    }
}