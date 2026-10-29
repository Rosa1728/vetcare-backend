package pe.edu.utec.vetcare.service;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.vetcare.entity.MascotaEntity;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.repository.MascotaRepository;
import pe.edu.utec.vetcare.security.AccesoClinico;

@Service
public class FotoMascotaService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final MascotaRepository mascotas;
    private final MascotaService permisos;

    public FotoMascotaService(MascotaRepository mascotas, MascotaService permisos) {
        this.mascotas = mascotas;
        this.permisos = permisos;
    }

    @Transactional(readOnly = true)
    public byte[] leer(Long id) {
        permisos.obtener(id); // Misma pertenencia que la ficha clínica, nunca una URL pública.
        byte[] foto = buscar(id).getFotoPerfil();
        if (foto == null) throw new ResourceNotFoundException("La mascota no tiene fotografía");
        return foto;
    }

    @Transactional
    public void guardar(Long id, MultipartFile foto) {
        MascotaEntity mascota = gestionar(id);
        mascota.setFotoPerfil(normalizar(foto));
        mascotas.saveAndFlush(mascota);
    }

    @Transactional
    public void eliminar(Long id) {
        MascotaEntity mascota = gestionar(id);
        mascota.setFotoPerfil(null);
        mascotas.saveAndFlush(mascota);
    }

    private MascotaEntity buscar(Long id) {
        return mascotas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada"));
    }

    private MascotaEntity gestionar(Long id) {
        MascotaEntity mascota = buscar(id);
        if (!AccesoClinico.es("ADMIN") && !(AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(mascota)))
            throw new AccessDeniedException("No puedes modificar la fotografía de esta mascota");
        return mascota;
    }

    private byte[] normalizar(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) throw new BadRequestException("Selecciona una fotografía");
        if (foto.getSize() > MAX_BYTES) throw new BadRequestException("La fotografía no debe superar 5 MB");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(foto.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("La imagen es inválida o está corrupta");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!java.util.Set.of("png", "jpeg", "jpg", "webp").contains(format))
                    throw new BadRequestException("Solo se admiten JPG, PNG o WebP");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 16_777_216L)
                    throw new BadRequestException("La imagen no debe superar 16 megapíxeles");
                if (width != height) throw new BadRequestException("Confirma el recorte cuadrado antes de subir la fotografía");
                BufferedImage original = reader.read(0);
                if (original == null) throw new BadRequestException("La imagen está corrupta");
                BufferedImage avatar = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                var graphics = avatar.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(original, 0, 0, 512, 512, null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                ImageIO.write(avatar, "png", output);
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new BadRequestException("La imagen es inválida o está corrupta");
        }
    }
}
