package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @Entity
@Table(name = "historia_publicacion")
public class HistoriaPublicacionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_publicacion") private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_mascota", nullable = false)
    private MascotaEntity mascota;
    @Column(nullable = false, length = 120) private String titulo;
    @Column(nullable = false, length = 40) private String categoria;
    @Column(nullable = false, length = 3000) private String contenido;
    @Column(name = "foto_archivo", length = 100) private String fotoArchivo;
    @Column(name = "fecha_creacion", nullable = false, updatable = false) private LocalDateTime fechaCreacion;
    @PrePersist void fecha() { if (fechaCreacion == null) fechaCreacion = LocalDateTime.now(); }
}
