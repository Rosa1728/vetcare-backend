package pe.edu.utec.vetcare.dto.response;

import java.time.LocalDateTime;
import pe.edu.utec.vetcare.enums.EstadoAlergias;

public record AntecedentesResponse(
        EstadoAlergias estadoAlergias,
        String alergiasSustancias, String alergiasReacciones, String alergiasObservaciones,
        String enfermedadesRelevantes, String cirugiasPrevias, String notasMedicas, String alertasMedicas,
        LocalDateTime actualizadoEn, String veterinarioNombre, long version
) {}
