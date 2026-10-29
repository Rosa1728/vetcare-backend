package pe.edu.utec.vetcare.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.utec.vetcare.dto.request.MascotaRequest;
import pe.edu.utec.vetcare.dto.response.MascotaResponse;
import pe.edu.utec.vetcare.entity.MascotaEntity;

@Mapper(componentModel = "spring")
public interface MascotaMapper {

    @Mapping(target = "idMascota", ignore = true)
    @Mapping(target = "propietario", ignore = true)
    @Mapping(target = "fotoPerfil", ignore = true)
    MascotaEntity toEntity(MascotaRequest request);

    @Mapping(
            source = "propietario.idPropietario",
            target = "idPropietario"
    )
    @Mapping(
            expression = "java(entity.getPropietario().getUsuario().getNombres() + \" \" + entity.getPropietario().getUsuario().getApellidos())",
            target = "nombrePropietario"
    )
    @Mapping(target = "tieneFoto", expression = "java(entity.getFotoPerfil() != null)")
    MascotaResponse toResponse(MascotaEntity entity);

    @Mapping(target = "idMascota", ignore = true)
    @Mapping(target = "propietario", ignore = true)
    @Mapping(target = "fotoPerfil", ignore = true)
    void updateEntity(
            MascotaRequest request,
            @MappingTarget MascotaEntity entity
    );
}