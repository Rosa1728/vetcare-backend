package pe.edu.utec.vetcare.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.vetcare.dto.request.CrearVacunaRequest;
import pe.edu.utec.vetcare.dto.request.AplicarVacunaRequest;
import pe.edu.utec.vetcare.dto.response.VacunaResponse;
import pe.edu.utec.vetcare.entity.*;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.security.AccesoClinico;
import java.time.LocalDate;
import java.time.ZoneId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import pe.edu.utec.vetcare.dto.request.ProgramacionVacunaRequest;
import pe.edu.utec.vetcare.dto.request.NotaVacunaRequest;
import pe.edu.utec.vetcare.exception.EstadoVacunaInvalidoException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VacunaService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final java.time.Clock clock;
    private final VacunaRepository vacunas;
    private final MascotaRepository mascotas;
    private final VeterinarioRepository veterinarios;
    private final AtencionRepository atenciones;
    private final EntityManager entityManager;
    private final RecordatorioVacunaService recordatorios;

    private void puedeConsultar(MascotaEntity mascota) {
        if (AccesoClinico.es("ADMIN") || (AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(mascota))) return;
        if (AccesoClinico.es("VETERINARIO") && mascotas.findPacientesDeVeterinario(AccesoClinico.correo()).stream()
                .anyMatch(m -> m.getIdMascota().equals(mascota.getIdMascota()))) return;
        throw new AccessDeniedException("No tienes acceso a las vacunas de esta mascota");
    }

    private MascotaEntity mascota(Long id) {
        return mascotas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada"));
    }

    private VacunaResponse toResponse(VacunaEntity v) {
        String estado = v.getProximaDosis() == null ? "SIN_PROXIMA_DOSIS" :
                v.getProximaDosis().isBefore(LocalDate.now(clock.withZone(LIMA))) ? "VENCIDA" :
                v.getProximaDosis().isEqual(LocalDate.now(clock.withZone(LIMA))) ? "HOY" : "PROGRAMADA";
        return new VacunaResponse(v.getIdVacuna(), v.getMascota().getIdMascota(),
                v.getVeterinario().getIdVeterinario(), v.getAtencion() == null ? null : v.getAtencion().getIdAtencion(),
                v.getNombre(), v.getFechaAplicacion(), v.getProximaDosis(), v.getObservaciones(), estado,
                v.getFechaAplicacion() == null ? "PROGRAMADA" : "APLICADA", estado, v.getFechaProgramada(), v.getIdVacunaOrigen());
    }

    @Transactional(readOnly = true)
    public List<VacunaResponse> listarPorMascota(Long idMascota) {
        MascotaEntity m = mascota(idMascota);
        puedeConsultar(m);
        return vacunas.findByMascota_IdMascotaOrderByProximaDosisAsc(idMascota).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VacunaResponse obtener(Long id) {
        VacunaEntity vacuna = vacunas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacuna no encontrada"));
        puedeConsultar(vacuna.getMascota());
        return toResponse(vacuna);
    }

    @Transactional
    public VacunaResponse registrar(CrearVacunaRequest request) {
        return registrarEnAtencion(request,false);
    }

    private VacunaResponse registrarEnAtencion(CrearVacunaRequest request,boolean duranteRegistro) {
        if (!AccesoClinico.es("VETERINARIO"))
            throw new AccessDeniedException("Solo un veterinario puede registrar vacunas o programar dosis");
        validarFechas(request.fechaAplicacion(), request.proximaDosis());
        MascotaEntity m = mascota(request.idMascota());
        VeterinarioEntity vet = veterinarios.findById(request.idVeterinario())
                .orElseThrow(() -> new ResourceNotFoundException("Veterinario no encontrado"));
        if (!vet.getUsuario().getCorreo().equals(AccesoClinico.correo()))
            throw new AccessDeniedException("Solo puedes registrar vacunas a tu nombre");
        if (request.fechaAplicacion() == null && request.proximaDosis() == null)
            throw new BadRequestException("Indica fechaAplicacion o proximaDosis");
        if (request.fechaAplicacion() != null && request.idAtencion() == null)
            throw new BadRequestException("Una vacuna aplicada debe vincularse a una atención clínica");
        if (request.fechaAplicacion() == null && request.idAtencion() != null)
            throw new BadRequestException("Una vacuna programada no debe asociarse a una atención aplicada");
        if (request.fechaAplicacion() != null && request.proximaDosis() != null &&
                !request.proximaDosis().isAfter(request.fechaAplicacion()))
            throw new BadRequestException("La próxima dosis debe ser posterior a la fecha de aplicación");
        AtencionEntity atencion = null;
        if (request.idAtencion() != null) {
            atencion = atenciones.findById(request.idAtencion())
                    .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada"));
            if (!atencion.getCita().getMascota().getIdMascota().equals(m.getIdMascota()) ||
                    !atencion.getVeterinarioAtencion().getIdVeterinario().equals(vet.getIdVeterinario()))
                throw new BadRequestException("La atención no corresponde a la mascota y al veterinario indicados");
            if (request.fechaAplicacion().isAfter(atencion.getFechaAtencion().toLocalDate()))
                throw new BadRequestException("La fecha de aplicación no puede ser posterior a la atención");
        } else if (mascotas.findPacientesDeVeterinario(AccesoClinico.correo()).stream()
                .noneMatch(p -> p.getIdMascota().equals(m.getIdMascota()))) {
            throw new AccessDeniedException("No puedes programar vacunas de una mascota sin relación clínica");
        }
        if (atencion != null) validarAtencion(atencion,request.fechaAplicacion(),duranteRegistro,false);
        if (atencion != null) entityManager.lock(atencion, LockModeType.PESSIMISTIC_WRITE);
        if (atencion != null && vacunas.existsByAtencion_IdAtencionAndNombreIgnoreCase(atencion.getIdAtencion(), request.nombre().trim()))
            throw new EstadoVacunaInvalidoException("Esta vacuna ya tiene una aplicación en la atención seleccionada");
        VacunaEntity v = new VacunaEntity();
        v.setMascota(m);
        v.setVeterinario(vet);
        v.setAtencion(atencion);
        v.setNombre(request.nombre().trim());
        v.setFechaAplicacion(request.fechaAplicacion());
        v.setProximaDosis(request.proximaDosis());
        v.setObservaciones(request.observaciones());
        v.setFechaProgramada(v.getFechaAplicacion() == null ? v.getProximaDosis() : null);
        vacunas.saveAndFlush(v);
        programarSiguiente(v);
        recordatorios.sincronizar(v);
        return toResponse(v);
    }

    /** Confirma una dosis previamente programada sin perder el registro ni crear otro. */
    @Transactional
    public VacunaResponse aplicar(Long idVacuna, AplicarVacunaRequest request) {
        return aplicarEnAtencion(idVacuna,request,false);
    }

    private VacunaResponse aplicarEnAtencion(Long idVacuna, AplicarVacunaRequest request, boolean duranteRegistro) {
        if (!AccesoClinico.es("VETERINARIO"))
            throw new AccessDeniedException("Solo un veterinario puede confirmar vacunas aplicadas");
        VacunaEntity vacuna = vacunas.findLockedById(idVacuna)
                .orElseThrow(() -> new ResourceNotFoundException("Vacuna no encontrada"));
        if (!vacuna.getVeterinario().getUsuario().getCorreo().equals(AccesoClinico.correo()))
            throw new AccessDeniedException("Solo el veterinario responsable puede confirmar esta vacuna");
        if (vacuna.getFechaAplicacion() != null || vacuna.getAtencion() != null)
            throw new EstadoVacunaInvalidoException("La vacuna ya está registrada como aplicada");

        validarFechas(request.fechaAplicacion(), request.proximaDosis());
        AtencionEntity atencion = atenciones.findById(request.idAtencion())
                .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada"));
        if (!atencion.getCita().getMascota().getIdMascota().equals(vacuna.getMascota().getIdMascota()) ||
                !atencion.getVeterinarioAtencion().getIdVeterinario().equals(vacuna.getVeterinario().getIdVeterinario()))
            throw new BadRequestException("La atención no corresponde a esta mascota y veterinario");
        LocalDate programada=vacuna.getFechaProgramada()!=null?vacuna.getFechaProgramada():vacuna.getProximaDosis();
        if(programada==null||request.fechaAplicacion().isBefore(programada))
            throw new BadRequestException("La aplicación no puede ser anterior a la fecha programada de vacunación");
        validarAtencion(atencion,request.fechaAplicacion(),duranteRegistro,true);
        if (!request.fechaAplicacion().equals(atencion.getFechaAtencion().toLocalDate()))
            throw new BadRequestException("La fecha de aplicación debe corresponder al día de la atención clínica");
        if (request.proximaDosis() != null && !request.proximaDosis().isAfter(request.fechaAplicacion()))
            throw new BadRequestException("La próxima dosis debe ser posterior a la aplicación");

        entityManager.lock(atencion, LockModeType.PESSIMISTIC_WRITE);
        if (vacunas.existsByAtencion_IdAtencionAndNombreIgnoreCaseAndIdVacunaNot(atencion.getIdAtencion(), vacuna.getNombre(), vacuna.getIdVacuna()))
            throw new EstadoVacunaInvalidoException("Esta vacuna ya tiene una aplicación en la atención seleccionada");
        // Solo conocemos la fecha programada de registros que todavía no se aplicaron.
        if (vacuna.getFechaProgramada() == null) vacuna.setFechaProgramada(vacuna.getProximaDosis());
        vacuna.setAtencion(atencion);
        vacuna.setFechaAplicacion(request.fechaAplicacion());
        vacuna.setProximaDosis(request.proximaDosis());
        if (request.observaciones() != null && !request.observaciones().isBlank()) vacuna.setObservaciones(request.observaciones());
        vacunas.saveAndFlush(vacuna);
        programarSiguiente(vacuna);
        return toResponse(vacuna);
    }

    /** Reutiliza las mismas autorizaciones y validaciones dentro de la transacción clínica. */
    @Transactional
    public void registrarDuranteAtencion(AtencionEntity atencion,
            List<pe.edu.utec.vetcare.dto.request.VacunacionAtencionRequest> registros) {
        if (!AccesoClinico.es("VETERINARIO")) throw new AccessDeniedException("Solo el veterinario puede registrar vacunación clínica");
        var seleccionadas = new java.util.HashSet<Long>();
        var nombres = new java.util.HashSet<String>();
        for (var r : registros) {
            if (r.aplicada() == null) throw new BadRequestException("Indica si la vacuna fue aplicada");
            if (r.idVacunaProgramada() != null && !seleccionadas.add(r.idVacunaProgramada()))
                throw new EstadoVacunaInvalidoException("La dosis programada está repetida");
            if (!r.aplicada() && (r.idVacunaProgramada() != null || r.fechaAplicacion() != null))
                throw new BadRequestException("Una programación futura no es una aplicación");
            if (r.aplicada() && r.fechaAplicacion() == null) throw new BadRequestException("Indica la fecha efectiva de aplicación");
            if (r.aplicada() && r.fechaAplicacion().isAfter(atencion.getFechaAtencion().toLocalDate()))
                throw new BadRequestException("La aplicación no puede ser posterior a la atención");
            if (r.idVacunaProgramada() != null) {
                // La fila conserva su identidad; aplicar() bloquea y rechaza una segunda aplicación.
                aplicarEnAtencion(r.idVacunaProgramada(), new AplicarVacunaRequest(atencion.getIdAtencion(), r.fechaAplicacion(), r.proximaDosis(), r.observaciones()),true);
            } else {
                if (r.nombre() == null || r.nombre().isBlank()) throw new BadRequestException("Indica el nombre de la vacuna");
                if (!nombres.add(r.nombre().trim().toLowerCase(java.util.Locale.ROOT)))
                    throw new EstadoVacunaInvalidoException("La vacuna está repetida en esta atención");
                registrarEnAtencion(new CrearVacunaRequest(atencion.getCita().getMascota().getIdMascota(),
                        atencion.getVeterinarioAtencion().getIdVeterinario(), r.aplicada() ? atencion.getIdAtencion() : null,
                        r.nombre(), r.aplicada() ? r.fechaAplicacion() : null, r.proximaDosis(), r.observaciones()),true);
            }
        }
    }

    private void programarSiguiente(VacunaEntity aplicada) {
        if (aplicada.getFechaAplicacion() == null || aplicada.getProximaDosis() == null) return;
        VacunaEntity siguiente = new VacunaEntity();
        siguiente.setMascota(aplicada.getMascota()); siguiente.setVeterinario(aplicada.getVeterinario());
        siguiente.setNombre(aplicada.getNombre()); siguiente.setProximaDosis(aplicada.getProximaDosis());
        siguiente.setFechaProgramada(aplicada.getProximaDosis()); siguiente.setIdVacunaOrigen(aplicada.getIdVacuna());
        siguiente.setObservaciones(aplicada.getObservaciones()); vacunas.saveAndFlush(siguiente);
        recordatorios.sincronizar(siguiente);
    }

    private void validarAtencion(AtencionEntity atencion, LocalDate aplicacion, boolean duranteRegistro, boolean mismoDia) {
        var cita=atencion.getCita();
        boolean estadoValido=cita!=null && (cita.getEstado()==pe.edu.utec.vetcare.enums.EstadoCita.ATENDIDA ||
                (duranteRegistro && cita.getEstado()==pe.edu.utec.vetcare.enums.EstadoCita.CONFIRMADA));
        if(!estadoValido)throw new BadRequestException("La aplicación requiere una cita y atención clínica válidas; no admite citas canceladas o sin asistencia");
        if(!cita.getVeterinario().getIdVeterinario().equals(atencion.getVeterinarioAtencion().getIdVeterinario()) ||
                !atencion.getHistoriaClinica().getMascota().getIdMascota().equals(cita.getMascota().getIdMascota()))
            throw new BadRequestException("La cita, la atención y el expediente deben corresponder al mismo paciente y veterinario");
        if((mismoDia ? !aplicacion.equals(atencion.getFechaAtencion().toLocalDate()) : aplicacion.isAfter(atencion.getFechaAtencion().toLocalDate())) ||
                atencion.getFechaAtencion().isBefore(cita.getFechaCita()))
            throw new BadRequestException("La fecha real de aplicación debe corresponder a la atención clínica (Lima)");
    }

    private void validarFechas(LocalDate aplicacion, LocalDate proxima) {
        LocalDate hoy = LocalDate.now(clock.withZone(LIMA));
        if (aplicacion != null && aplicacion.isAfter(hoy)) throw new BadRequestException("La aplicación no puede estar en el futuro (Lima)");
        if (proxima != null && proxima.isBefore(hoy)) throw new BadRequestException("La próxima dosis no puede estar en el pasado (Lima)");
    }

    private VacunaEntity propiaBloqueada(Long id) {
        if (!AccesoClinico.es("VETERINARIO")) throw new AccessDeniedException("Solo el veterinario responsable puede modificar vacunas");
        VacunaEntity v = vacunas.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Vacuna no encontrada"));
        if (!v.getVeterinario().getUsuario().getCorreo().equals(AccesoClinico.correo())) throw new AccessDeniedException("Registro de otro veterinario");
        puedeConsultar(v.getMascota());
        return v;
    }

    @Transactional
    public VacunaResponse actualizarProgramacion(Long id, ProgramacionVacunaRequest request) {
        VacunaEntity v = propiaBloqueada(id);
        if (v.getFechaAplicacion() != null || v.getAtencion() != null) throw new EstadoVacunaInvalidoException("Una aplicación no puede reprogramarse ni cambiar de identidad");
        validarFechas(null, request.proximaDosis());
        v.setNombre(request.nombre().trim());
        v.setProximaDosis(request.proximaDosis());
        v.setFechaProgramada(request.proximaDosis());
        v.setObservaciones(request.observaciones());
        vacunas.saveAndFlush(v);
        recordatorios.sincronizar(v);
        return toResponse(v);
    }

    @Transactional
    public VacunaResponse actualizarNota(Long id, NotaVacunaRequest request) {
        VacunaEntity v = propiaBloqueada(id);
        if (v.getFechaAplicacion() == null) throw new EstadoVacunaInvalidoException("Utiliza la edición de programación para una dosis pendiente");
        v.setObservaciones(request.observaciones());
        return toResponse(vacunas.saveAndFlush(v));
    }

    @Transactional(readOnly = true)
    public List<VacunaResponse> proximas(int dias) {
        if (dias < 0 || dias > 365) throw new BadRequestException("dias debe estar entre 0 y 365");
        LocalDate hoy = LocalDate.now(clock.withZone(LIMA));
        return vacunas.findByProximaDosisBetweenOrderByProximaDosisAsc(hoy, hoy.plusDays(dias)).stream()
                .filter(v -> !vacunas.existsByIdVacunaOrigen(v.getIdVacuna()))
                .filter(v -> AccesoClinico.es("ADMIN") ||
                        (AccesoClinico.es("PROPIETARIO") && AccesoClinico.esPropietario(v.getMascota())) ||
                        (AccesoClinico.es("VETERINARIO") && v.getVeterinario().getUsuario().getCorreo().equals(AccesoClinico.correo())))
                .map(this::toResponse).toList();
    }
}
