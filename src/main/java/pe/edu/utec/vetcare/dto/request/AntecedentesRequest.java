package pe.edu.utec.vetcare.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.utec.vetcare.enums.EstadoAlergias;

/** Sustitución explícita del resumen de antecedentes; no modifica atenciones ni vacunas. */
public record AntecedentesRequest(
        @NotNull EstadoAlergias estadoAlergias,
        @Size(max = 2000) String alergiasSustancias,
        @Size(max = 2000) String alergiasReacciones,
        @Size(max = 2000) String alergiasObservaciones,
        @Size(max = 4000) String enfermedadesRelevantes,
        @Size(max = 4000) String cirugiasPrevias,
        @Size(max = 4000) String notasMedicas,
        @Size(max = 2000) String alertasMedicas,
        @NotNull @jakarta.validation.constraints.PositiveOrZero Long version,
        @Size(max = 500) String motivo,
        Long idAtencion
) {
    public AntecedentesRequest {
        alergiasSustancias=vacio(alergiasSustancias);alergiasReacciones=vacio(alergiasReacciones);
        alergiasObservaciones=vacio(alergiasObservaciones);enfermedadesRelevantes=vacio(enfermedadesRelevantes);
        cirugiasPrevias=vacio(cirugiasPrevias);notasMedicas=vacio(notasMedicas);alertasMedicas=vacio(alertasMedicas);
    }
    private static String vacio(String value){return value!=null&&value.isBlank()?null:value;}
}
