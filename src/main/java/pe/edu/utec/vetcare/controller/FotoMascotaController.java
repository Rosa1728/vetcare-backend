package pe.edu.utec.vetcare.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.vetcare.service.FotoMascotaService;

@RestController
@RequestMapping("/api/mascotas/{id}/foto")
public class FotoMascotaController {
    private final FotoMascotaService fotos;
    public FotoMascotaController(FotoMascotaService fotos) { this.fotos = fotos; }

    @GetMapping
    public ResponseEntity<byte[]> leer(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(fotos.leer(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PROPIETARIO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardar(@PathVariable Long id, @RequestParam("foto") MultipartFile foto) { fotos.guardar(id, foto); }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROPIETARIO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) { fotos.eliminar(id); }
}
