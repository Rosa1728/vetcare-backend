package pe.edu.utec.vetcare.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Getter @Setter @NoArgsConstructor @Entity @Table(name="regularizacion_atencion")
public class RegularizacionAtencionEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_regularizacion") private Long idRegularizacion;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_cita",nullable=false) private CitaEntity cita;
 @Column(name="id_veterinario",nullable=false) private Long idVeterinario;
 @Column(nullable=false,length=500) private String motivo;
 @Column(name="fecha_solicitud",nullable=false) private LocalDateTime fechaSolicitud;
 @Column(nullable=false,length=20) private String estado;
 @Column(name="id_administrador") private Long idAdministrador;
 @Column(name="fecha_resolucion") private LocalDateTime fechaResolucion;
 @Column(name="vence_en") private LocalDateTime venceEn;
 @Column(name="consumida_en") private LocalDateTime consumidaEn;
}
