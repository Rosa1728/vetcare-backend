package pe.edu.utec.vetcare.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AtencionResponse {

    private Long idAtencion;

    // PROVISIONAL
    private Long idHistoria;

    private Long idVeterinario;

    private Long idCita;

    private LocalDateTime fechaAtencion;

    private BigDecimal precioAtencion;

    private BigDecimal peso;

    private String sintoma;

    private String diagnostico;

    private String tratamiento;

    private String observacion;
}