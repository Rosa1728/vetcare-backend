package pe.edu.utec.vetcare.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="recuperacion_contrasena") @Getter @Setter
public class RecuperacionContrasenaEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=150) private String correo;
 @Column(name="id_usuario") private Long idUsuario;
 @Column(name="token_hash",length=64,unique=true) private String tokenHash;
 @Column(name="creado_en",nullable=false) private Instant creadoEn;
 @Column(name="vence_en") private Instant venceEn;
 @Column(name="usado_en") private Instant usadoEn;
 @Column(nullable=false,length=12) private String estado="PENDIENTE";
 @Column(nullable=false) private int intentos;
}
