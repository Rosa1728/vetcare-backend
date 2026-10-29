package pe.edu.utec.vetcare.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.CreateAtencionRequest;
import pe.edu.utec.vetcare.dto.request.UpdateAtencionRequest;
import pe.edu.utec.vetcare.dto.response.AtencionResponse;
import pe.edu.utec.vetcare.service.AtencionService;

import java.util.List;

@RestController
@RequestMapping("/api/atenciones")
public class AtencionController {

    private final AtencionService atencionService;

    public AtencionController(AtencionService atencionService) {
        this.atencionService = atencionService;
    }

    @GetMapping
    public ResponseEntity<List<AtencionResponse>> getAll() {
        return ResponseEntity.ok(atencionService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AtencionResponse> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(atencionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AtencionResponse> create(
            @Valid @RequestBody CreateAtencionRequest request
    ) {
        return ResponseEntity.ok(atencionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AtencionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAtencionRequest request
    ) {
        return ResponseEntity.ok(atencionService.update(id, request));
    }

    @PatchMapping("/{id}/precio")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AtencionResponse> precio(@PathVariable Long id,@Valid @RequestBody pe.edu.utec.vetcare.dto.request.PrecioAtencionRequest request){
        return ResponseEntity.ok(atencionService.actualizarPrecio(id,request.getPrecioAtencion()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        atencionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}