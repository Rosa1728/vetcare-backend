package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.*;
public record SolicitarRecuperacionRequest(@NotBlank @Email @Size(max=150) String correo){}
