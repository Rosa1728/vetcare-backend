package pe.edu.utec.vetcare.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PropietarioResponse {

    private Long idPropietario;

    private Long idUsuario;

    private String nombres;

    private String apellidos;

    private String correo;
    private String tipoDocumento;
    private String nroDocumento;
    private String nroCelular;
    private boolean fotoDisponible;

    private String direccion;

    private LocalDate fechaRegistroClinica;

    private String estadoCliente;
}