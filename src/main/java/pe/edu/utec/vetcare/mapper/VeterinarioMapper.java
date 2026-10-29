package pe.edu.utec.vetcare.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.utec.vetcare.dto.request.CreateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.request.UpdateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.response.VeterinarioResponse;
import pe.edu.utec.vetcare.entity.VeterinarioEntity;

@Mapper(componentModel = "spring")
public interface VeterinarioMapper {

    @Mapping(target = "idVeterinario", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "tratamientoProfesional", ignore = true)
    VeterinarioEntity toEntity(CreateVeterinarioRequest request);

    @Mapping(
            source = "usuario.idUsuario",
            target = "idUsuario"
    )
    @Mapping(source = "usuario.nombres", target = "nombres")
    @Mapping(source = "usuario.apellidos", target = "apellidos")
    @Mapping(target = "fotoDisponible", expression = "java(entity.getUsuario().getFotoPerfil() != null)")
    VeterinarioResponse toResponse(VeterinarioEntity entity);

    @Mapping(target = "idVeterinario", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "tratamientoProfesional", ignore = true)
    void updateEntity(
            UpdateVeterinarioRequest request,
            @MappingTarget VeterinarioEntity entity
    );
}