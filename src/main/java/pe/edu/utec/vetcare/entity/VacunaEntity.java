package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "vacuna")
@Getter @Setter
public class VacunaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_vacuna")
    private Long idVacuna;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_mascota", nullable = false)
    private MascotaEntity mascota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_veterinario", nullable = false)
    private VeterinarioEntity veterinario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_atencion")
    private AtencionEntity atencion;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "fecha_aplicacion")
    private LocalDate fechaAplicacion;

    @Column(name = "proxima_dosis")
    private LocalDate proximaDosis;

    @Column(name = "fecha_programada")
    private LocalDate fechaProgramada;

    @Column(name = "id_vacuna_origen")
    private Long idVacunaOrigen;

    @Column(name = "observaciones", length = 500)
    private String observaciones;
}
