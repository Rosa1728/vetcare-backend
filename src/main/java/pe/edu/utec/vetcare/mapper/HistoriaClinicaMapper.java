package pe.edu.utec.vetcare.mapper;

import org.mapstruct.Mapper;
import pe.edu.utec.vetcare.dto.response.AntecedentesResponse;
import org.mapstruct.Mapping;
import pe.edu.utec.vetcare.dto.response.HistoriaClinicaResponse;
import pe.edu.utec.vetcare.entity.HistoriaClinicaEntity;

@Mapper(componentModel = "spring")
public interface HistoriaClinicaMapper {

    @Mapping(
            source = "mascota.idMascota",
            target = "idMascota"
    )
    @Mapping(
            source = "mascota.nombre",
            target = "nombreMascota"
    )
    @Mapping(source = "mascota.propietario.usuario.nroCelular", target = "telefonoPropietario")
    @Mapping(target = "antecedentes", expression = "java(antecedentes(entity))")
    HistoriaClinicaResponse toResponse(
            HistoriaClinicaEntity entity
    );
    default AntecedentesResponse antecedentes(HistoriaClinicaEntity h) {
        String nombre = null;
        if (h.getAntecedentesVeterinario() != null) {
            var u = h.getAntecedentesVeterinario().getUsuario();
            nombre = u.getNombres() + " " + u.getApellidos();
        }
        return new AntecedentesResponse(
                h.getEstadoAlergias(), h.getAlergiasSustancias(), h.getAlergiasReacciones(),
                h.getAlergiasObservaciones(), h.getEnfermedadesRelevantes(), h.getCirugiasPrevias(),
                h.getNotasMedicas(), h.getAlertasMedicas(), h.getAntecedentesActualizadosEn(), nombre, h.getVersionAntecedentes());
    }
}
