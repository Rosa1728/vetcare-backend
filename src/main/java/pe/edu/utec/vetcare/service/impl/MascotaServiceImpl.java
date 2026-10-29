package pe.edu.utec.vetcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utec.vetcare.security.AccesoClinico;
import pe.edu.utec.vetcare.repository.CitaRepository;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.dto.request.MascotaRequest;
import pe.edu.utec.vetcare.dto.response.MascotaResponse;
import pe.edu.utec.vetcare.entity.HistoriaClinicaEntity;
import pe.edu.utec.vetcare.entity.MascotaEntity;
import pe.edu.utec.vetcare.entity.PropietarioEntity;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.mapper.MascotaMapper;
import pe.edu.utec.vetcare.repository.HistoriaClinicaRepository;
import pe.edu.utec.vetcare.repository.MascotaRepository;
import pe.edu.utec.vetcare.repository.PropietarioRepository;
import pe.edu.utec.vetcare.service.MascotaService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MascotaServiceImpl
        implements MascotaService {

    private final MascotaRepository mascotaRepository;
    private final CitaRepository citaRepository;

    private final PropietarioRepository propietarioRepository;

    private final HistoriaClinicaRepository historiaClinicaRepository;

    private final MascotaMapper mascotaMapper;

    @Override
    @Transactional(readOnly = true)
    public List<MascotaResponse> listar() {

        List<MascotaEntity> mascotas;
        if (AccesoClinico.es("ADMIN")) mascotas = mascotaRepository.findAll();
        else if (AccesoClinico.es("PROPIETARIO")) mascotas = mascotaRepository.findByCorreoPropietario(AccesoClinico.correo());
        else if (AccesoClinico.es("VETERINARIO")) mascotas = mascotaRepository.findPacientesDeVeterinario(AccesoClinico.correo());
        else throw new AccessDeniedException("Acceso denegado");
        return mascotas.stream()
                .map(mascotaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MascotaResponse obtener(Long id) {

        MascotaEntity mascota = buscarMascota(id);
        comprobarLectura(mascota);
        return mascotaMapper.toResponse(mascota);
    }

    @Override
    @Transactional
    public MascotaResponse crear(
            MascotaRequest request
    ) {

        PropietarioEntity propietario =
                propietarioRepository
                        .findById(
                                request.getIdPropietario()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Propietario no encontrado"
                                )
                        );

        if (!AccesoClinico.es("ADMIN") && !(AccesoClinico.es("PROPIETARIO")
                && propietario.getUsuario().getCorreo().equals(AccesoClinico.correo()))) {
            throw new AccessDeniedException("Solo puede registrar sus propias mascotas");
        }

        MascotaEntity mascota =
                mascotaMapper.toEntity(request);

        mascota.setPropietario(propietario);

        MascotaEntity mascotaGuardada =
                mascotaRepository.save(mascota);

        HistoriaClinicaEntity historia =
                new HistoriaClinicaEntity();

        historia.setMascota(mascotaGuardada);

        historiaClinicaRepository.save(historia);

        return mascotaMapper.toResponse(
                mascotaGuardada
        );
    }

    @Override
    @Transactional
    public MascotaResponse actualizar(
            Long id,
            MascotaRequest request
    ) {

        MascotaEntity mascota = buscarMascota(id);
        comprobarEscritura(mascota);
        if (!AccesoClinico.es("ADMIN") && !mascota.getPropietario().getIdPropietario().equals(request.getIdPropietario())) {
            throw new AccessDeniedException("No puede transferir una mascota a otro propietario");
        }

        PropietarioEntity propietario =
                propietarioRepository
                        .findById(
                                request.getIdPropietario()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Propietario no encontrado"
                                )
                        );

        mascotaMapper.updateEntity(
                request,
                mascota
        );

        mascota.setPropietario(propietario);

        return mascotaMapper.toResponse(
                mascotaRepository.save(mascota)
        );
    }

    @Override
    @Transactional
    public void eliminar(Long id) {

        MascotaEntity mascota = buscarMascota(id);
        comprobarEscritura(mascota);
        if (citaRepository.existsByMascota_IdMascota(id)) {
            throw new BadRequestException("No se puede eliminar una mascota con citas registradas");
        }
        if (historiaClinicaRepository.findByMascotaIdMascota(id).isPresent()) {
            throw new BadRequestException("No se puede eliminar una mascota con historia clínica registrada");
        }
        mascotaRepository.delete(mascota);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MascotaResponse>
    listarPorPropietario(Long idPropietario) {

        if (!AccesoClinico.es("ADMIN")) {
            if (!AccesoClinico.es("PROPIETARIO") || !propietarioRepository.findById(idPropietario)
                    .map(pr -> pr.getUsuario().getCorreo().equals(AccesoClinico.correo())).orElse(false)) {
                throw new AccessDeniedException("No puede consultar mascotas de otro propietario");
            }
        }
        return mascotaRepository
                .findByPropietarioIdPropietario(
                        idPropietario
                )
                .stream()
                .map(mascotaMapper::toResponse)
                .toList();
    }

    private void comprobarLectura(MascotaEntity mascota) {
        if (AccesoClinico.es("ADMIN") || (AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(mascota))
                || (AccesoClinico.es("VETERINARIO") && citaRepository.existsByMascota_IdMascotaAndVeterinario_Usuario_Correo(
                        mascota.getIdMascota(), AccesoClinico.correo()))) return;
        throw new AccessDeniedException("No puede consultar esta mascota");
    }

    private void comprobarEscritura(MascotaEntity mascota) {
        if (AccesoClinico.es("ADMIN") || (AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(mascota))) return;
        throw new AccessDeniedException("No puede modificar esta mascota");
    }

    private MascotaEntity buscarMascota(Long id) {

        return mascotaRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Mascota no encontrada"
                        )
                );
    }
}