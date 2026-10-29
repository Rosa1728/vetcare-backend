package pe.edu.utec.vetcare.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.vetcare.entity.UsuarioEntity;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.repository.UsuarioRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javax.imageio.ImageIO;

@Service
public class FotoPerfilService {
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private final UsuarioRepository usuarios;
    private final Path carpeta;

    public FotoPerfilService(UsuarioRepository usuarios,
            @Value("${vetcare.perfiles.directorio:./uploads/perfiles}") String directorio) {
        this.usuarios = usuarios;
        this.carpeta = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Transactional
    public void guardar(Long id, MultipartFile foto) {
        UsuarioEntity usuario = usuario(id);
        if (foto == null || foto.isEmpty()) throw new BadRequestException("Selecciona una fotografía");
        if (foto.getSize() > MAX_BYTES) throw new BadRequestException("La fotografía no debe superar 2 MB");
        try {
            byte[] datos = foto.getBytes();
            String tipo = detectarTipo(datos);
            if (tipo == null) throw new BadRequestException("Solo se admiten imágenes JPG o PNG válidas");
            String extension = tipo.equals("image/png") ? ".png" : ".jpg";
            Files.createDirectories(carpeta);
            String nombre = UUID.randomUUID() + extension;
            Path destino = carpeta.resolve(nombre);
            Path temporal = Files.createTempFile(carpeta, "perfil-", ".tmp");
            try {
                Files.write(temporal, datos);
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporal); }
            String anterior = usuario.getFotoPerfil();
            usuario.setFotoPerfil(nombre);
            try { usuarios.saveAndFlush(usuario); }
            catch (RuntimeException ex) { Files.deleteIfExists(destino); throw ex; }
            borrarAnterior(anterior);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo almacenar la fotografía", ex);
        }
    }

    @Transactional(readOnly = true)
    public Foto leer(Long id) {
        String nombre = usuario(id).getFotoPerfil();
        if (nombre == null) throw new ResourceNotFoundException("El usuario no tiene fotografía");
        Path archivo = rutaSegura(nombre);
        try {
            byte[] datos = Files.readAllBytes(archivo);
            String tipo = detectarTipo(datos);
            if (tipo == null) throw new ResourceNotFoundException("Fotografía no disponible");
            return new Foto(datos, MediaType.parseMediaType(tipo));
        } catch (IOException ex) { throw new ResourceNotFoundException("Fotografía no disponible"); }
    }

    @Transactional
    public void eliminar(Long id) {
        UsuarioEntity usuario = usuario(id);
        String anterior = usuario.getFotoPerfil();
        usuario.setFotoPerfil(null);
        usuarios.saveAndFlush(usuario);
        borrarAnterior(anterior);
    }

    private UsuarioEntity usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Usuario autenticado no encontrado"));
    }

    private Path rutaSegura(String nombre) {
        Path archivo = carpeta.resolve(nombre).normalize();
        if (!archivo.getParent().equals(carpeta)) throw new IllegalStateException("Nombre de archivo inválido");
        return archivo;
    }

    private void borrarAnterior(String nombre) {
        if (nombre == null) return;
        try { Files.deleteIfExists(rutaSegura(nombre)); }
        catch (IOException ignored) { /* La referencia actual permanece consistente. */ }
    }

    private String detectarTipo(byte[] datos) throws IOException {
        if (datos.length < 8) return null;
        boolean png = (datos[0] & 255) == 137 && datos[1] == 80 && datos[2] == 78 && datos[3] == 71
                && datos[4] == 13 && datos[5] == 10 && datos[6] == 26 && datos[7] == 10;
        boolean jpg = (datos[0] & 255) == 255 && (datos[1] & 255) == 216 && (datos[2] & 255) == 255;
        if (!png && !jpg) return null;
        if (ImageIO.read(new ByteArrayInputStream(datos)) == null) return null;
        return png ? "image/png" : "image/jpeg";
    }

    public record Foto(byte[] contenido, MediaType tipo) {}
}
