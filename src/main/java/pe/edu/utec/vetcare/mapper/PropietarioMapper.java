package pe.edu.utec.vetcare.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import pe.edu.utec.vetcare.dto.request.PropietarioRequest;
import pe.edu.utec.vetcare.dto.response.PropietarioResponse;
import pe.edu.utec.vetcare.entity.PropietarioEntity;

@Mapper(componentModel = "spring")
public interface PropietarioMapper {

    @Mapping(target = "idPropietario", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "fechaRegistroClinica", ignore = true)
    @Mapping(target = "estadoCliente", ignore = true)
    PropietarioEntity toEntity(
            PropietarioRequest request
    );

    @Mapping(
            source = "usuario.idUsuario",
            target = "idUsuario"
    )
    @Mapping(
            source = "usuario.nombres",
            target = "nombres"
    )
    @Mapping(
            source = "usuario.apellidos",
            target = "apellidos"
    )
    @Mapping(source="usuario.correo", target="correo")
    @Mapping(source="usuario.tipoDocumento", target="tipoDocumento")
    @Mapping(source="usuario.nroDocumento", target="nroDocumento")
    @Mapping(source="usuario.nroCelular", target="nroCelular")
    @Mapping(target="fotoDisponible", expression="java(entity.getUsuario().getFotoPerfil()!=null)")
    PropietarioResponse toResponse(
            PropietarioEntity entity
    );

    @Mapping(target = "idPropietario", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "fechaRegistroClinica", ignore = true)
    @Mapping(target = "estadoCliente", ignore = true)
    void updateEntity(
            PropietarioRequest request,
            @MappingTarget PropietarioEntity entity
    );
}