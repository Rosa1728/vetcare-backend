package pe.edu.utec.vetcare.dto.response;

public record LoginResponse(String token, String tipo, long expiraEnSegundos) {}
