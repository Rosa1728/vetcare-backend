package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import pe.edu.utec.vetcare.enums.EstadoAlergias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "historia_clinica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoriaClinicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historia")
    private Long idHistoria;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_mascota",
            nullable = false,
            unique = true
    )
    private MascotaEntity mascota;

    @Column(
            name = "fecha_apertura",
            nullable = false
    )
    private LocalDateTime fechaApertura;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_alergias", nullable = false, length = 30)
    private EstadoAlergias estadoAlergias = EstadoAlergias.NO_EVALUADAS;

    @Column(name = "alergias_sustancias", length = 2000)
    private String alergiasSustancias;
    @Column(name = "alergias_reacciones", length = 2000)
    private String alergiasReacciones;
    @Column(name = "alergias_observaciones", length = 2000)
    private String alergiasObservaciones;
    @Column(name = "enfermedades_relevantes", length = 4000)
    private String enfermedadesRelevantes;
    // Texto clínico íntegro: admite varios procedimientos y sus fechas conocidas, sin inferirlas.
    @Column(name = "cirugias_previas", length = 4000)
    private String cirugiasPrevias;
    @Column(name = "notas_medicas", length = 4000)
    private String notasMedicas;
    @Column(name = "alertas_medicas", length = 2000)
    private String alertasMedicas;
    @Column(name = "antecedentes_actualizados_en")
    private LocalDateTime antecedentesActualizadosEn;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "antecedentes_id_veterinario")
    private VeterinarioEntity antecedentesVeterinario;

    @Version
    @Column(name = "version_antecedentes", nullable = false)
    private long versionAntecedentes;

    @PrePersist
    public void prePersist() {

        if (fechaApertura == null) {
            fechaApertura = LocalDateTime.now();
        }
    }
}
