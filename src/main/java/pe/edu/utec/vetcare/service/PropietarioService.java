package pe.edu.utec.vetcare.service;

import pe.edu.utec.vetcare.dto.response.UsuarioResponse;
import pe.edu.utec.vetcare.dto.request.ActualizarPerfilPropietarioRequest;
import pe.edu.utec.vetcare.dto.request.PropietarioRequest;
import pe.edu.utec.vetcare.dto.response.PropietarioResponse;

import java.util.List;

public interface PropietarioService {

    List<PropietarioResponse> listar();

    PropietarioResponse obtener(Long id);

    PropietarioResponse obtenerMio();

    PropietarioResponse crear(
            PropietarioRequest request
    );

    PropietarioResponse actualizar(
            Long id,
            PropietarioRequest request
    );

    PropietarioResponse actualizarMiDireccion(String direccion);

    UsuarioResponse actualizarMiPerfil(
            ActualizarPerfilPropietarioRequest request);

    void eliminar(Long id);
}