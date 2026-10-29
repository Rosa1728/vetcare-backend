package pe.edu.utec.vetcare.service;

import pe.edu.utec.vetcare.dto.request.MascotaRequest;
import pe.edu.utec.vetcare.dto.response.MascotaResponse;

import java.util.List;

public interface MascotaService {

    List<MascotaResponse> listar();

    MascotaResponse obtener(Long id);

    MascotaResponse crear(MascotaRequest request);

    MascotaResponse actualizar(
            Long id,
            MascotaRequest request
    );

    void eliminar(Long id);

    List<MascotaResponse>
    listarPorPropietario(Long idPropietario);
}