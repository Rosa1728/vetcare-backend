package pe.edu.utec.vetcare.service;

import pe.edu.utec.vetcare.dto.request.CreateAtencionRequest;
import pe.edu.utec.vetcare.dto.request.UpdateAtencionRequest;
import pe.edu.utec.vetcare.dto.response.AtencionResponse;

import java.util.List;

public interface AtencionService {

    List<AtencionResponse> getAll();

    AtencionResponse getById(Long id);

    AtencionResponse create(CreateAtencionRequest request);

    AtencionResponse update(Long id, UpdateAtencionRequest request);

    AtencionResponse actualizarPrecio(Long id, java.math.BigDecimal precio);

    void delete(Long id);
}