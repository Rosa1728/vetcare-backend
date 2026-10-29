package pe.edu.utec.vetcare.dto.response;

public record PerfilVeterinarioResponse(
        Long idVeterinario,
        Long idUsuario,
        String nombres,
        String apellidos,
        String correo,
        String nroCelular,
        String especialidad,
        String nroColegiatura,
        String tratamientoProfesional,
        String direccion) {
}
