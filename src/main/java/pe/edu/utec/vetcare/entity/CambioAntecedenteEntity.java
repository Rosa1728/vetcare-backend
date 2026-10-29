package pe.edu.utec.vetcare.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
/** Evento inmutable. No es otra historia clínica. */
@Entity @Table(name="antecedente_cambio") @Immutable
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) @AllArgsConstructor
public class CambioAntecedenteEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_historia",nullable=false) private HistoriaClinicaEntity historia;
 @Column(nullable=false,length=20) private String seccion;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,String> anterior;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,String> nuevo;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_veterinario",nullable=false) private VeterinarioEntity veterinario;
 @Column(name="nombre_veterinario",nullable=false,length=201) private String nombreVeterinario;
 @Column(nullable=false) private Instant fecha;
 @Column(length=500) private String motivo;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_atencion") private AtencionEntity atencion;
 @Column(nullable=false) private long revision;
}
