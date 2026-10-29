package pe.edu.utec.vetcare.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.utec.vetcare.dto.request.UpdateAtencionRequest;
import pe.edu.utec.vetcare.dto.response.AtencionResponse;
import pe.edu.utec.vetcare.entity.AtencionEntity;

@Mapper(componentModel = "spring")
public interface AtencionMapper {

    @Mapping(
            source = "historiaClinica.idHistoria",
            target = "idHistoria"
    )
    @Mapping(
            source = "veterinarioAtencion.idVeterinario",
            target = "idVeterinario"
    )
    @Mapping(
            source = "cita.idCita",
            target = "idCita"
    )
    AtencionResponse toResponse(AtencionEntity entity);

    @Mapping(target = "idAtencion", ignore = true)
    @Mapping(target = "historiaClinica", ignore = true)
    @Mapping(target = "veterinarioAtencion", ignore = true)
    @Mapping(target = "cita", ignore = true)
    @Mapping(target = "fechaAtencion", ignore = true)
    @Mapping(target = "precioAtencion", ignore = true)
    void updateEntity(
            UpdateAtencionRequest request,
            @MappingTarget AtencionEntity entity
    );
}