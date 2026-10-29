package pe.edu.utec.vetcare.service;

import pe.edu.utec.vetcare.dto.response.HistoriaClinicaResponse;
import pe.edu.utec.vetcare.dto.request.AntecedentesRequest;

public interface HistoriaClinicaService {
    pe.edu.utec.vetcare.dto.response.CambioAntecedenteResponse.Pagina cambiosAntecedentes(Long id,String seccion,int pagina,int size);

    HistoriaClinicaResponse obtener(Long id);

    HistoriaClinicaResponse actualizarAntecedentes(Long id, AntecedentesRequest request);

    HistoriaClinicaResponse
    obtenerPorMascota(Long idMascota);
}