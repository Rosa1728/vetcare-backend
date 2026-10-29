package pe.edu.utec.vetcare.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.CrearVacunaRequest;
import pe.edu.utec.vetcare.dto.request.AplicarVacunaRequest;
import pe.edu.utec.vetcare.dto.response.VacunaResponse;
import pe.edu.utec.vetcare.service.VacunaService;
import java.util.List;
import pe.edu.utec.vetcare.dto.request.ProgramacionVacunaRequest;
import pe.edu.utec.vetcare.dto.request.NotaVacunaRequest;

@RestController
@RequestMapping("/api/vacunas")
public class VacunaController {
    private final VacunaService servicio;
    public VacunaController(VacunaService servicio) { this.servicio = servicio; }

    @PostMapping
    public ResponseEntity<VacunaResponse> registrar(@Valid @RequestBody CrearVacunaRequest request) {
        return ResponseEntity.ok(servicio.registrar(request));
    }

    @PutMapping("/{id}/aplicar")
    public VacunaResponse aplicar(@PathVariable Long id, @Valid @RequestBody AplicarVacunaRequest request) {
        return servicio.aplicar(id, request);
    }

    @PutMapping("/{id}/programacion")
    public VacunaResponse actualizarProgramacion(@PathVariable Long id, @Valid @RequestBody ProgramacionVacunaRequest request) {
        return servicio.actualizarProgramacion(id, request);
    }

    @PatchMapping("/{id}/observaciones")
    public VacunaResponse actualizarNota(@PathVariable Long id, @Valid @RequestBody NotaVacunaRequest request) {
        return servicio.actualizarNota(id, request);
    }

    @GetMapping("/mascota/{idMascota}")
    public List<VacunaResponse> porMascota(@PathVariable Long idMascota) {
        return servicio.listarPorMascota(idMascota);
    }

    @GetMapping("/proximas")
    public List<VacunaResponse> proximas(@RequestParam(defaultValue = "30") int dias) {
        return servicio.proximas(dias);
    }

    @GetMapping("/{id}")
    public VacunaResponse obtener(@PathVariable Long id) { return servicio.obtener(id); }
}
