package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "veterinario")
public class VeterinarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_veterinario")
    private Long idVeterinario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_usuario",
            nullable = false,
            unique = true
    )
    private UsuarioEntity usuario;

    @Column(name = "tratamiento_profesional", length = 4)
    private String tratamientoProfesional;

    @Column(name = "especialidad")
    private String especialidad;

    @Column(name = "nro_colegiatura")
    private String nroColegiatura;
}