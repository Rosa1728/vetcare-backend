package pe.edu.utec.vetcare.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UsuarioResponse {

    private Long idUsuario;

    private String nombres;

    private String apellidos;

    private String tipoDocumento;

    private String nroDocumento;

    private String correo;

    private String nroCelular;
    private String direccionContacto;

    private Long idRol;

    private String rol;

    private LocalDateTime fechaRegistro;

    private String fotoPerfilUrl;
    private boolean fotoDisponible;
}