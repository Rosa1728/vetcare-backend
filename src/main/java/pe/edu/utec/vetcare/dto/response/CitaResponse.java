package pe.edu.utec.vetcare.dto.response;

import lombok.Data;
import pe.edu.utec.vetcare.enums.EstadoCita;

import java.time.LocalDateTime;

@Data
public class CitaResponse {

    private Long idCita;

    private Long idVeterinario;

    // PROVISIONAL
    private Long idMascota;

    private LocalDateTime fechaCita;

    private EstadoCita estado;

    private String motivoCita;
    private String motivoCancelacion;
    private String detalleCancelacion;
    private Long idUsuarioCancelacion;
    private String rolCancelacion;
    private LocalDateTime fechaCancelacion;
    private LocalDateTime horaServidor;
    private LocalDateTime finRegistroAtencion;
    private boolean puedeCancelar;
    private boolean puedeReprogramar;
    private boolean puedeIniciarAtencion;
    private boolean puedeGuardarAtencion;
    private RegularizacionResponse regularizacion;
}
