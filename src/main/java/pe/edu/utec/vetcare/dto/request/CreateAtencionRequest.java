package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAtencionRequest {


    @Size(max = 20, message = "Registra como máximo 20 vacunas por atención")
    private java.util.List<@NotNull @jakarta.validation.Valid VacunacionAtencionRequest> vacunas = java.util.List.of();

    @NotNull(message = "La historia clínica es obligatoria")
    private Long idHistoria;

    @NotNull(message = "El veterinario es obligatorio")
    private Long idVeterinario;

    @NotNull(message = "La cita es obligatoria")
    private Long idCita;

    @DecimalMin(value = "0.0", inclusive = false,
            message = "El precio debe ser mayor a 0")
    private BigDecimal precioAtencion;

    @NotNull(message = "El peso es obligatorio")
    @Positive(message = "El peso debe ser mayor a 0")
    private BigDecimal peso;

    @NotBlank(message = "El síntoma es obligatorio")
    @Size(max = 500, message = "El síntoma no puede superar 500 caracteres")
    private String sintoma;

    @NotBlank(message = "El diagnóstico es obligatorio")
    @Size(max = 500, message = "El diagnóstico no puede superar 500 caracteres")
    private String diagnostico;

    @NotBlank(message = "El tratamiento es obligatorio")
    @Size(max = 500, message = "El tratamiento no puede superar 500 caracteres")
    private String tratamiento;

    @Size(max = 500, message = "La observación no puede superar 500 caracteres")
    private String observacion;
}