package pe.edu.utec.vetcare.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utec.vetcare.entity.HistoriaPublicacionEntity;
import pe.edu.utec.vetcare.entity.MascotaEntity;
import pe.edu.utec.vetcare.repository.HistoriaPublicacionRepository;
import pe.edu.utec.vetcare.repository.MascotaRepository;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/publicaciones") @RequiredArgsConstructor
public class HistoriaPublicacionController {
    private final HistoriaPublicacionRepository publicaciones;
    private final MascotaRepository mascotas;
    @Value("${VETCARE_HISTORIAS_DIR:uploads/historias}") private String directorio;

    public record Publicacion(Long id, Long idMascota, String mascota, String titulo, String categoria,
                              String contenido, String autor, LocalDateTime fechaCreacion, boolean foto) {}

    private Publicacion dto(HistoriaPublicacionEntity h) {
        var u = h.getMascota().getPropietario().getUsuario();
        return new Publicacion(h.getId(), h.getMascota().getIdMascota(), h.getMascota().getNombre(),
            h.getTitulo(), h.getCategoria(), h.getContenido(), u.getNombres() + " " + u.getApellidos(),
            h.getFechaCreacion(), h.getFotoArchivo() != null);
    }
    private HistoriaPublicacionEntity encontrar(Long id) {
        return publicaciones.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no encontrada"));
    }
    private void propietario(HistoriaPublicacionEntity h, Authentication auth) {
        if (!h.getMascota().getPropietario().getUsuario().getCorreo().equalsIgnoreCase(auth.getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar esta historia");
    }
    @GetMapping public List<Publicacion> publicas() { return publicaciones.findTop40ByOrderByFechaCreacionDesc().stream().map(this::dto).toList(); }
    @GetMapping("/mis") @PreAuthorize("hasRole('PROPIETARIO')")
    public List<Publicacion> mias(Authentication auth) {
        return publicaciones.findByMascotaPropietarioUsuarioCorreoOrderByFechaCreacionDesc(auth.getName()).stream().map(this::dto).toList();
    }
    @PostMapping @PreAuthorize("hasRole('PROPIETARIO')") @ResponseStatus(HttpStatus.CREATED)
    public Publicacion crear(@RequestBody Nueva req, Authentication auth) {
        if (req == null || req.idMascota() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una mascota");
        MascotaEntity mascota = mascotas.findById(req.idMascota()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Mascota no encontrada"));
        if (!mascota.getPropietario().getUsuario().getCorreo().equalsIgnoreCase(auth.getName())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La mascota no te pertenece");
        validar(req);
        HistoriaPublicacionEntity h = new HistoriaPublicacionEntity();h.setMascota(mascota);
        h.setTitulo(req.titulo().trim());h.setContenido(req.contenido().trim());h.setCategoria(req.categoria().trim());
        return dto(publicaciones.save(h));
    }
    public record Nueva(Long idMascota,String titulo,String categoria,String contenido) {}
    private void validar(Nueva req) {
        if (req.titulo() == null || req.titulo().isBlank() || req.titulo().length()>120 ||
            req.categoria()==null || !List.of("Adopción","Recuperación","Celebración","Aventuras","Otros").contains(req.categoria()) ||
            req.contenido()==null || req.contenido().isBlank() || req.contenido().length()>3000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Revisa título, categoría y contenido");
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('PROPIETARIO')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, Authentication auth) throws IOException {
        var h = encontrar(id); propietario(h,auth);
        if (h.getFotoArchivo()!=null) Files.deleteIfExists(Path.of(directorio).resolve(h.getFotoArchivo()));
        publicaciones.delete(h);
    }
    @PostMapping(value="/{id}/foto",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PROPIETARIO')")
    public Publicacion foto(@PathVariable Long id, @RequestPart("foto") MultipartFile file, Authentication auth) throws IOException {
        var h = encontrar(id);propietario(h,auth);
        if (file==null || file.isEmpty() || file.getSize()>5_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La imagen debe pesar menos de 5 MB");
        byte[] data=file.getBytes();
        boolean png=data.length>8 && (data[0]&255)==137 && (data[1]&255)==80 && (data[2]&255)==78 && (data[3]&255)==71;
        boolean jpg=data.length>3 && (data[0]&255)==255 && (data[1]&255)==216 && (data[2]&255)==255;
        boolean webp=data.length>12 && new String(data,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(data,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        if (!png && !jpg && !webp) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solo se admiten imágenes JPG, PNG o WebP");
        Path folder=Path.of(directorio).toAbsolutePath().normalize();Files.createDirectories(folder);
        String archivo=UUID.randomUUID()+(png?".png":webp?".webp":".jpg");
        Files.write(folder.resolve(archivo),data,StandardOpenOption.CREATE_NEW);
        String anterior=h.getFotoArchivo(); h.setFotoArchivo(archivo); publicaciones.save(h);
        if(anterior!=null) Files.deleteIfExists(folder.resolve(anterior));
        return dto(h);
    }
    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> fotoPublica(@PathVariable Long id) throws IOException {
        var h=encontrar(id);if(h.getFotoArchivo()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path archivo=Path.of(directorio).resolve(h.getFotoArchivo());
        if(!Files.isRegularFile(archivo))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MediaType tipo=h.getFotoArchivo().endsWith(".png")?MediaType.IMAGE_PNG:
            h.getFotoArchivo().endsWith(".webp")?MediaType.parseMediaType("image/webp"):MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(tipo).cacheControl(CacheControl.noCache()).body(Files.readAllBytes(archivo));
    }
}
