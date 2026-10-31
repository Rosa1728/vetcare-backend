package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateVeterinarioRequest {
    @Pattern(regexp="Dr[.]|Dra[.]|", message="Selecciona Dr., Dra. o Sin tratamiento")
    private String tratamientoProfesional;
    @Valid
    private ActualizarUsuarioRequest datosUsuario;
    @NotBlank(message="La especialidad es obligatoria")
    @Size(max=100)
    private String especialidad;

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rechazarCampo(String nombre,Object valor) {
        throw new pe.edu.utec.vetcare.exception.BadRequestException("Solo se permite editar contacto, especialidad y tratamiento profesional. La identidad y la colegiatura están protegidas.");
    }
}
