package pe.edu.utec.vetcare.service;

import pe.edu.utec.vetcare.dto.request.CreateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.request.ActualizarPerfilVeterinarioRequest;
import pe.edu.utec.vetcare.dto.response.PerfilVeterinarioResponse;
import pe.edu.utec.vetcare.dto.request.RegistrarVeterinarioRequest;
import pe.edu.utec.vetcare.dto.request.UpdateVeterinarioRequest;
import pe.edu.utec.vetcare.dto.response.VeterinarioResponse;

import java.util.List;

public interface VeterinarioService {

    List<VeterinarioResponse> getAll();

    PerfilVeterinarioResponse miPerfil(String correoAutenticado);

    PerfilVeterinarioResponse actualizarMiPerfil(String correoAutenticado, ActualizarPerfilVeterinarioRequest request);

    VeterinarioResponse getById(Long id);

    VeterinarioResponse create(CreateVeterinarioRequest request);

    VeterinarioResponse registrar(RegistrarVeterinarioRequest request);

    VeterinarioResponse update(
            Long id,
            UpdateVeterinarioRequest request
    );

    void delete(Long id);
}