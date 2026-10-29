package pe.edu.utec.vetcare.dto.response;

public record RegistroPropietarioResponse(
        Long idUsuario,
        Long idPropietario,
        String correo,
        String rol
) {}
