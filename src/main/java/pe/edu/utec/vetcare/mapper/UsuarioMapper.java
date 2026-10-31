package pe.edu.utec.vetcare.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.utec.vetcare.dto.request.UsuarioRequest;
import pe.edu.utec.vetcare.dto.request.ActualizarUsuarioRequest;
import pe.edu.utec.vetcare.dto.response.UsuarioResponse;
import pe.edu.utec.vetcare.entity.UsuarioEntity;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "idUsuario", ignore = true)
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "fechaRegistro", ignore = true)
    @Mapping(target = "fotoPerfil", ignore = true)
    UsuarioEntity toEntity(UsuarioRequest request);

    @Mapping(
            source = "rol.idRol",
            target = "idRol"
    )
    @Mapping(
            source = "rol.nombre",
            target = "rol"
    )
    @Mapping(target = "fotoPerfilUrl", ignore = true)
    @Mapping(target="fotoDisponible", expression="java(entity.getFotoPerfil() != null)")
    UsuarioResponse toResponse(UsuarioEntity entity);

    @org.mapstruct.BeanMapping(ignoreByDefault=true, nullValuePropertyMappingStrategy=org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target="nroCelular", source="nroCelular")
    void updateEntity(
            ActualizarUsuarioRequest request,
            @MappingTarget UsuarioEntity entity
    );
}