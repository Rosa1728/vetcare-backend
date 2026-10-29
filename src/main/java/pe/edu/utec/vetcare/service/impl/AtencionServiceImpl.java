package pe.edu.utec.vetcare.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.vetcare.security.AccesoClinico;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.dto.request.CreateAtencionRequest;
import pe.edu.utec.vetcare.dto.request.UpdateAtencionRequest;
import pe.edu.utec.vetcare.dto.response.AtencionResponse;
import pe.edu.utec.vetcare.entity.AtencionEntity;
import pe.edu.utec.vetcare.entity.CitaEntity;
import pe.edu.utec.vetcare.entity.HistoriaClinicaEntity;
import pe.edu.utec.vetcare.entity.VeterinarioEntity;
import pe.edu.utec.vetcare.enums.EstadoCita;
import pe.edu.utec.vetcare.exception.AtencionNoEncontradaException;
import pe.edu.utec.vetcare.exception.EstadoCitaInvalidoException;
import pe.edu.utec.vetcare.mapper.AtencionMapper;
import pe.edu.utec.vetcare.repository.AtencionRepository;
import pe.edu.utec.vetcare.repository.CitaRepository;
import pe.edu.utec.vetcare.repository.HistoriaClinicaRepository;
import pe.edu.utec.vetcare.repository.VeterinarioRepository;
import pe.edu.utec.vetcare.service.AtencionService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AtencionServiceImpl
        implements AtencionService {

    private final AtencionRepository atencionRepository;

    private final CitaRepository citaRepository;

    private final VeterinarioRepository veterinarioRepository;

    private final HistoriaClinicaRepository historiaClinicaRepository;

    private final AtencionMapper atencionMapper;
    private final pe.edu.utec.vetcare.service.VacunaService vacunas;
    private final pe.edu.utec.vetcare.service.SeguimientoCitaService seguimiento;
    private final pe.edu.utec.vetcare.service.ReglasHorarioAtencion horario;

    public AtencionServiceImpl(
            AtencionRepository atencionRepository,
            CitaRepository citaRepository,
            VeterinarioRepository veterinarioRepository,
            HistoriaClinicaRepository historiaClinicaRepository,
            AtencionMapper atencionMapper,
            pe.edu.utec.vetcare.service.SeguimientoCitaService seguimiento,
            pe.edu.utec.vetcare.service.ReglasHorarioAtencion horario,
            pe.edu.utec.vetcare.service.VacunaService vacunas
    ) {

        this.atencionRepository = atencionRepository;
        this.citaRepository = citaRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.historiaClinicaRepository =
                historiaClinicaRepository;
        this.atencionMapper = atencionMapper;
        this.vacunas = vacunas;
        this.seguimiento = seguimiento; this.horario = horario;
    }

    @Override
    public List<AtencionResponse> getAll() {

        return atencionRepository.findAll()
                .stream()
                .filter(this::puedeLeer)
                .map(atencionMapper::toResponse)
                .toList();
    }

    @Override
    public AtencionResponse getById(Long id) {

        AtencionEntity entity =
                atencionRepository.findById(id)
                        .orElseThrow(() ->
                                new AtencionNoEncontradaException(
                                        "Atención no encontrada"
                                )
                        );

        comprobarLectura(entity);
        return atencionMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public AtencionResponse create(
            CreateAtencionRequest request) {

        CitaEntity cita =
                citaRepository
                        .findByIdForUpdate(
                                request.getIdCita()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Cita no encontrada"
                                )
                        );

        comprobarEscrituraCita(cita);
        validarPrecio(request.getPrecioAtencion());
        var permiso = seguimiento.autorizarRegistro(cita);
        if (cita.getEstado()
                != EstadoCita.CONFIRMADA) {

            throw new EstadoCitaInvalidoException(
                    "La cita debe estar en estado CONFIRMADA para registrar sus datos clínicos"
            );
        }

        VeterinarioEntity veterinario =
                veterinarioRepository
                        .findById(
                                request.getIdVeterinario()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Veterinario no encontrado"
                                )
                        );

        if (!veterinario.getIdVeterinario().equals(cita.getVeterinario().getIdVeterinario())) {
            throw new BadRequestException("El veterinario de la atención debe ser el asignado a la cita");
        }

        HistoriaClinicaEntity historiaClinica =
                historiaClinicaRepository
                        .findById(
                                request.getIdHistoria()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Historia clínica no encontrada"
                                )
                        );

        boolean existeAtencion =
                atencionRepository
                        .existsByCita_IdCita(
                                request.getIdCita()
                        );

        if (existeAtencion) {

            throw new EstadoCitaInvalidoException(
                    "La cita ya tiene una atención registrada"
            );
        }

        /*
         * Validación adicional:
         * la historia clínica debe pertenecer
         * a la misma mascota de la cita.
         */
        if (!historiaClinica
                .getMascota()
                .getIdMascota()
                .equals(
                        cita.getMascota()
                                .getIdMascota()
                )) {

            throw new EstadoCitaInvalidoException(
                    "La historia clínica no pertenece a la mascota de la cita"
            );
        }

        AtencionEntity entity =
                new AtencionEntity();

        entity.setHistoriaClinica(
                historiaClinica
        );

        entity.setVeterinarioAtencion(
                veterinario
        );

        entity.setCita(cita);

        entity.setFechaAtencion(
                horario.ahora()
        );

        entity.setPrecioAtencion(
                request.getPrecioAtencion()
        );

        entity.setPeso(
                request.getPeso()
        );

        entity.setSintoma(
                request.getSintoma()
        );

        entity.setDiagnostico(
                request.getDiagnostico()
        );

        entity.setTratamiento(
                request.getTratamiento()
        );

        entity.setObservacion(
                request.getObservacion()
        );

        AtencionEntity savedEntity =
                atencionRepository.save(entity);

        // Toda la atención y sus vacunas se confirman o se revierten juntas.
        if (request.getVacunas() != null && !request.getVacunas().isEmpty()) {
            vacunas.registrarDuranteAtencion(savedEntity, request.getVacunas());
        }

        // Mismo @Transactional: si falla el registro, no se cambia la cita.
        cita.setEstado(EstadoCita.ATENDIDA);
        citaRepository.save(cita);
        seguimiento.consumir(permiso);

        return atencionMapper.toResponse(
                savedEntity
        );
    }

    @Override
    @Transactional
    public AtencionResponse update(
            Long id,
            UpdateAtencionRequest request) {

        AtencionEntity entity = buscarBloqueada(id);

        if (!AccesoClinico.es("VETERINARIO") || !AccesoClinico.esVeterinario(entity.getCita()))
            throw new AccessDeniedException("Solo el veterinario autor puede actualizar el contenido clínico");
        validarPrecio(request.getPrecioAtencion());
        atencionMapper.updateEntity(
                request,
                entity
        );

        if(request.getPrecioAtencion()!=null) entity.setPrecioAtencion(request.getPrecioAtencion());
        AtencionEntity updatedEntity =
                atencionRepository.save(entity);

        return atencionMapper.toResponse(
                updatedEntity
        );
    }

    @Override
    @Transactional
    public void delete(Long id) {

        AtencionEntity entity =
                atencionRepository.findById(id)
                        .orElseThrow(() ->
                                new AtencionNoEncontradaException(
                                        "Atención no encontrada"
                                )
                        );

        AccesoClinico.soloAdmin();
        throw new EstadoCitaInvalidoException("Las atenciones clínicas se conservan; no se permite su eliminación ordinaria");
    }
    private boolean puedeLeer(AtencionEntity entity) {
        return AccesoClinico.es("ADMIN") || (AccesoClinico.es("VETERINARIO") && AccesoClinico.esVeterinario(entity.getCita()))
                || (AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(entity.getCita()));
    }

    private void comprobarLectura(AtencionEntity entity) {
        if (!puedeLeer(entity)) throw new AccessDeniedException("No puede consultar esta atención");
    }

    private void comprobarEscrituraCita(CitaEntity cita) {
        if (AccesoClinico.es("ADMIN") || (AccesoClinico.es("VETERINARIO") && AccesoClinico.esVeterinario(cita))) return;
        throw new AccessDeniedException("Solo el veterinario asignado o ADMIN puede registrar o modificar la atención");
    }
    private void validarPrecio(java.math.BigDecimal precio){
        if(precio!=null && !AccesoClinico.es("ADMIN")) throw new AccessDeniedException("El precio solo puede ser establecido por ADMIN");
    }
    @Transactional
    public AtencionResponse actualizarPrecio(Long id, java.math.BigDecimal precio){
        AccesoClinico.soloAdmin();
        var entity=buscarBloqueada(id);
        entity.setPrecioAtencion(precio);return atencionMapper.toResponse(atencionRepository.save(entity));
    }
    private AtencionEntity buscarBloqueada(Long id){
        Long citaId=atencionRepository.citaId(id).orElseThrow(()->new AtencionNoEncontradaException("Atención no encontrada"));
        seguimiento.bloquear(citaId);
        return atencionRepository.findById(id).orElseThrow(()->new AtencionNoEncontradaException("Atención no encontrada"));
    }
}
