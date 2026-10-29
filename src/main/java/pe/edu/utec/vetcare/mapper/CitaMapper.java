package pe.edu.utec.vetcare.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.utec.vetcare.dto.request.CreateCitaRequest;
import pe.edu.utec.vetcare.dto.request.UpdateCitaRequest;
import pe.edu.utec.vetcare.dto.response.CitaResponse;
import pe.edu.utec.vetcare.entity.CitaEntity;

@Mapper(componentModel = "spring")
public interface CitaMapper {

    @Mapping(target = "idCita", ignore = true)
    @Mapping(target = "veterinario", ignore = true)
    @Mapping(target = "mascota", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target="inicioRegistro",ignore=true)
    @Mapping(target="idUsuarioInicio",ignore=true)
    @Mapping(target="motivoCancelacion",ignore=true)
    @Mapping(target="detalleCancelacion",ignore=true)
    @Mapping(target="idUsuarioCancelacion",ignore=true)
    @Mapping(target="rolCancelacion",ignore=true)
    @Mapping(target="fechaCancelacion",ignore=true)
    CitaEntity toEntity(CreateCitaRequest request);

    @Mapping(
            source = "veterinario.idVeterinario",
            target = "idVeterinario"
    )
    @Mapping(
            source = "mascota.idMascota",
            target = "idMascota"
    )
    @Mapping(target="puedeCancelar",ignore=true)
    @Mapping(target="puedeReprogramar",ignore=true)
    @Mapping(target="horaServidor",ignore=true)
    @Mapping(target="finRegistroAtencion",ignore=true)
    @Mapping(target="puedeIniciarAtencion",ignore=true)
    @Mapping(target="puedeGuardarAtencion",ignore=true)
    @Mapping(target="regularizacion",ignore=true)
    CitaResponse toResponse(CitaEntity entity);

    @Mapping(target = "idCita", ignore = true)
    @Mapping(target = "veterinario", ignore = true)
    @Mapping(target = "mascota", ignore = true)
    @Mapping(target = "estado", ignore = true)
    @Mapping(target="inicioRegistro",ignore=true)
    @Mapping(target="idUsuarioInicio",ignore=true)
    @Mapping(target="motivoCancelacion",ignore=true)
    @Mapping(target="detalleCancelacion",ignore=true)
    @Mapping(target="idUsuarioCancelacion",ignore=true)
    @Mapping(target="rolCancelacion",ignore=true)
    @Mapping(target="fechaCancelacion",ignore=true)
    void updateEntity(
            UpdateCitaRequest request,
            @MappingTarget CitaEntity entity
    );
}