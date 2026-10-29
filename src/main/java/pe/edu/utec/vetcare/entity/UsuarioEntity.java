package pe.edu.utec.vetcare.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(
            name = "nombres",
            nullable = false,
            length = 100
    )
    private String nombres;

    @Column(
            name = "apellidos",
            nullable = false,
            length = 100
    )
    private String apellidos;

    @Column(
            name = "tipo_documento",
            nullable = false,
            length = 20
    )
    private String tipoDocumento;

    @Column(
            name = "nro_documento",
            nullable = false,
            unique = true,
            length = 20
    )
    private String nroDocumento;

    @Column(
            name = "correo",
            nullable = false,
            unique = true,
            length = 150
    )
    private String correo;

    @Column(
            name = "contrasena",
            nullable = false,
            length = 255
    )
    private String contrasena;

    @Column(
            name = "nro_celular",
            length = 20
    )
    private String nroCelular;

    @Column(name = "direccion_contacto", length = 200)
    private String direccionContacto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_rol",
            nullable = false
    )
    private RolEntity rol;

    @Column(name = "foto_perfil", length = 255)
    private String fotoPerfil;

    @Column(
            name = "fecha_registro",
            nullable = false
    )
    private LocalDateTime fechaRegistro;

    @PrePersist
    public void prePersist() {

        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}