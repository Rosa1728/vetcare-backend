package pe.edu.utec.vetcare.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HistoriaClinicaResponse {

    private Long idHistoria;

    private Long idMascota;

    private String nombreMascota;

    private String telefonoPropietario;

    private LocalDateTime fechaApertura;

    private AntecedentesResponse antecedentes;
}