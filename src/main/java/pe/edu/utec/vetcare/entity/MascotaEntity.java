package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "mascota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MascotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mascota")
    private Long idMascota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_propietario",
            nullable = false
    )
    private PropietarioEntity propietario;

    @Column(
            name = "nombre",
            nullable = false,
            length = 100
    )
    private String nombre;

    @Column(
            name = "especie",
            nullable = false,
            length = 50
    )
    private String especie;

    @Column(
            name = "raza",
            length = 100
    )
    private String raza;

    @Column(
            name = "sexo",
            length = 10
    )
    private String sexo;

    @Column(name = "foto_perfil", columnDefinition = "bytea")
    private byte[] fotoPerfil;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;
}