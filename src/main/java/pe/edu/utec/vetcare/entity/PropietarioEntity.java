package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.vetcare.enums.EstadoCliente;

import java.time.LocalDate;

@Entity
@Table(name = "propietario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropietarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_propietario")
    private Long idPropietario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_usuario",
            nullable = false,
            unique = true
    )
    private UsuarioEntity usuario;

    @Column(
            name = "direccion",
            length = 200
    )
    private String direccion;

    @Column(
            name = "fecha_registro_clinica",
            nullable = false
    )
    private LocalDate fechaRegistroClinica;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "estado_cliente",
            nullable = false,
            length = 20
    )
    private EstadoCliente estadoCliente;

    @PrePersist
    public void prePersist() {

        if (fechaRegistroClinica == null) {
            fechaRegistroClinica = LocalDate.now();
        }

        if (estadoCliente == null) {
            estadoCliente = EstadoCliente.ACTIVO;
        }
    }
}