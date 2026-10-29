package pe.edu.utec.vetcare.dto.request;
import jakarta.validation.constraints.Size;
public record NotaVacunaRequest(@Size(max=500) String observaciones) {}
