package pe.edu.utec.vetcare.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import pe.edu.utec.vetcare.dto.request.AntecedentesRequest;
import pe.edu.utec.vetcare.enums.EstadoAlergias;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.repository.VeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utec.vetcare.security.AccesoClinico;
import pe.edu.utec.vetcare.repository.CitaRepository;
import pe.edu.utec.vetcare.dto.response.HistoriaClinicaResponse;
import pe.edu.utec.vetcare.entity.HistoriaClinicaEntity;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.mapper.HistoriaClinicaMapper;
import pe.edu.utec.vetcare.repository.HistoriaClinicaRepository;
import pe.edu.utec.vetcare.service.HistoriaClinicaService;

@Service
@RequiredArgsConstructor
public class HistoriaClinicaServiceImpl
        implements HistoriaClinicaService {

    private final HistoriaClinicaRepository repository;

    private final HistoriaClinicaMapper mapper;
    private final CitaRepository citaRepository;
    private final VeterinarioRepository veterinarios;
    private final pe.edu.utec.vetcare.service.AuditoriaAntecedentes auditoria;

    @Override
    @Transactional(readOnly = true)
    public HistoriaClinicaResponse obtener(Long id) {

        HistoriaClinicaEntity entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Historia clínica no encontrada"
                                )
                        );

        comprobarLectura(entity);
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public HistoriaClinicaResponse
    obtenerPorMascota(Long idMascota) {

        HistoriaClinicaEntity entity =
                repository
                        .findByMascotaIdMascota(
                                idMascota
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Historia clínica no encontrada"
                                )
                        );

        comprobarLectura(entity);
        return mapper.toResponse(entity);
    }
    @Override
    @Transactional
    public HistoriaClinicaResponse actualizarAntecedentes(Long id, AntecedentesRequest request) {
        if (!AccesoClinico.es("VETERINARIO")) throw new AccessDeniedException("Solo el veterinario autorizado puede registrar antecedentes");
        var historia = repository.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Historia clínica no encontrada"));
        comprobarLectura(historia);
        var estado = request.estadoAlergias();
        boolean sustancias = request.alergiasSustancias() != null && !request.alergiasSustancias().isBlank();
        boolean reacciones = request.alergiasReacciones() != null && !request.alergiasReacciones().isBlank();
        if (estado == EstadoAlergias.ALERGIAS_REGISTRADAS && !sustancias)
            throw new BadRequestException("Indica las sustancias o medicamentos registrados como alergia");
        if (estado != EstadoAlergias.ALERGIAS_REGISTRADAS && (sustancias || reacciones))
            throw new BadRequestException("El estado de evaluación no es coherente con las alergias indicadas");
        var veterinario=veterinarios.findByUsuario_Correo(AccesoClinico.correo()).orElseThrow(() -> new ResourceNotFoundException("Perfil veterinario no encontrado"));
        if(!auditoria.registrar(historia,request,veterinario))return mapper.toResponse(historia);
        historia.setEstadoAlergias(estado);
        historia.setAlergiasSustancias(request.alergiasSustancias());
        historia.setAlergiasReacciones(request.alergiasReacciones());
        historia.setAlergiasObservaciones(request.alergiasObservaciones());
        historia.setEnfermedadesRelevantes(request.enfermedadesRelevantes());
        historia.setCirugiasPrevias(request.cirugiasPrevias());
        historia.setNotasMedicas(request.notasMedicas());
        historia.setAlertasMedicas(request.alertasMedicas());
        historia.setAntecedentesActualizadosEn(auditoria.ahora());
        historia.setAntecedentesVeterinario(veterinario);
        return mapper.toResponse(repository.saveAndFlush(historia));
    }

    @Transactional(readOnly=true)
    public pe.edu.utec.vetcare.dto.response.CambioAntecedenteResponse.Pagina cambiosAntecedentes(Long id,String seccion,int pagina,int size){
        var historia=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Historia clínica no encontrada"));
        comprobarLectura(historia);return auditoria.consultar(id,seccion,pagina,size);
    }

    private void comprobarLectura(HistoriaClinicaEntity historia) {
        var mascota = historia.getMascota();
        if (AccesoClinico.es("ADMIN") || (AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(mascota))
                || (AccesoClinico.es("VETERINARIO") && citaRepository.existsByMascota_IdMascotaAndVeterinario_Usuario_Correo(
                        mascota.getIdMascota(), AccesoClinico.correo()))) return;
        throw new AccessDeniedException("No puede consultar esta historia clínica");
    }
}
