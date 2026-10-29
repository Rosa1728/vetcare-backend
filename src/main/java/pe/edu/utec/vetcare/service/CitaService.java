package pe.edu.utec.vetcare.service;

import pe.edu.utec.vetcare.dto.request.CreateCitaRequest;
import pe.edu.utec.vetcare.dto.request.UpdateCitaRequest;
import pe.edu.utec.vetcare.dto.response.CitaResponse;

import java.util.List;

public interface CitaService {

    pe.edu.utec.vetcare.dto.response.DisponibilidadCitaResponse disponibilidad(Long veterinario,java.time.LocalDate fecha,Long excluir);

    List<CitaResponse> getAll();

    CitaResponse getById(Long id);

    CitaResponse create(CreateCitaRequest request);

    CitaResponse update(Long id, UpdateCitaRequest request);

    void delete(Long id);

    CitaResponse confirm(Long id);

    CitaResponse cancel(Long id, pe.edu.utec.vetcare.dto.request.CancelarCitaRequest request);

    CitaResponse attend(Long id);
}