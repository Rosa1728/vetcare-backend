package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recordatorio_vacuna", uniqueConstraints = @UniqueConstraint(
        name = "uq_recordatorio_dosis_tipo", columnNames = {"id_vacuna", "fecha_dosis", "tipo"}))
@Getter @Setter
public class RecordatorioVacunaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recordatorio")
    private Long idRecordatorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_vacuna", nullable = false)
    private VacunaEntity vacuna;

    @Column(name = "fecha_dosis", nullable = false)
    private LocalDate fechaDosis;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "leido_en")
    private LocalDateTime leidoEn;

    @Column(name = "correo_enviado_en")
    private LocalDateTime correoEnviadoEn;

    @PrePersist
    void antesDeGuardar() { if (creadoEn == null) creadoEn = LocalDateTime.now(); }
}
