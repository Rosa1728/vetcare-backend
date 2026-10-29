package pe.edu.utec.vetcare.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MascotaResponse {

    private Long idMascota;

    private Long idPropietario;

    private String nombrePropietario;

    private String nombre;

    private String especie;

    private String raza;

    private String sexo;

    private LocalDate fechaNacimiento;

    private boolean tieneFoto;
}