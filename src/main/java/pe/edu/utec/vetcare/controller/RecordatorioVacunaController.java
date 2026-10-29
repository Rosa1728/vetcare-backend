package pe.edu.utec.vetcare.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.response.RecordatorioVacunaResponse;
import pe.edu.utec.vetcare.service.RecordatorioVacunaService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recordatorios")
@RequiredArgsConstructor
public class RecordatorioVacunaController {
    private final RecordatorioVacunaService servicio;

    @GetMapping("/mis")
    public List<RecordatorioVacunaResponse> mis() { return servicio.misRecordatorios(); }

    @PatchMapping("/{id}/leido")
    public RecordatorioVacunaResponse leido(@PathVariable Long id) { return servicio.marcarLeido(id); }

    @PostMapping("/generar")
    public Map<String, Integer> generar() { return Map.of("creados", servicio.generarManualmente()); }
}
