package pe.edu.utec.vetcare.dto.response;

import lombok.Data;

@Data
public class VeterinarioResponse {

    private Long idVeterinario;
    private Long idUsuario;
    private boolean fotoDisponible;
    private String nombres;
    private String apellidos;
    private String especialidad;
    private String nroColegiatura;
    private String tratamientoProfesional;
}