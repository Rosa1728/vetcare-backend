package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "atenciones")
public class AtencionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atencion")
    private Long idAtencion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_historia",
            nullable = false
    )
    private HistoriaClinicaEntity historiaClinica;

    @ManyToOne
    @JoinColumn(name = "id_veterinario_atencion", nullable = false)
    private VeterinarioEntity veterinarioAtencion;


    @OneToOne
    @JoinColumn(name = "id_cita", nullable = false, unique = true)
    private CitaEntity cita;


    @Column(name = "fecha_atencion", nullable = false)
    private LocalDateTime fechaAtencion;


    @Column(name = "precio_atencion")
    private BigDecimal precioAtencion;


    @Column(name = "peso")
    private BigDecimal peso;


    @Column(name = "sintoma", length = 500)
    private String sintoma;


    @Column(name = "diagnostico", length = 500)
    private String diagnostico;


    @Column(name = "tratamiento", length = 500)
    private String tratamiento;


    @Column(name = "observacion", length = 500)
    private String observacion;
}