package pe.edu.utec.vetcare.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.vetcare.dto.response.RecordatorioVacunaResponse;
import pe.edu.utec.vetcare.entity.RecordatorioVacunaEntity;
import pe.edu.utec.vetcare.entity.VacunaEntity;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.repository.RecordatorioVacunaRepository;
import pe.edu.utec.vetcare.repository.VacunaRepository;
import pe.edu.utec.vetcare.security.AccesoClinico;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordatorioVacunaService {
    private final RecordatorioVacunaRepository recordatorios;
    private final VacunaRepository vacunas;
    private final java.time.Clock clock;
    private static final ZoneId LIMA=ZoneId.of("America/Lima");

    private LocalDate hoy() { return LocalDate.now(clock.withZone(LIMA)); }

    private RecordatorioVacunaResponse convertir(RecordatorioVacunaEntity r) {
        VacunaEntity v = r.getVacuna();
        String fecha=r.getFechaDosis().format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu",java.util.Locale.forLanguageTag("es-PE")));
        String mensaje="🐾 " + v.getMascota().getNombre() + " tiene " +
                ("VENCIDA".equals(r.getTipo()) ? "pendiente" : "programada") + " su vacuna " + v.getNombre() +
                " para el " + fecha + ". Revisa los detalles en Mis vacunas.";
        return new RecordatorioVacunaResponse(r.getIdRecordatorio(), v.getIdVacuna(),
                v.getMascota().getIdMascota(), v.getMascota().getNombre(), v.getNombre(),
                r.getFechaDosis(), r.getTipo(), mensaje, r.getCreadoEn(), r.getLeidoEn() != null);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioVacunaResponse> misRecordatorios() {
        if (!AccesoClinico.es("PROPIETARIO"))
            throw new AccessDeniedException("Solo el propietario consulta sus recordatorios");
        return recordatorios.findByVacuna_Mascota_Propietario_Usuario_CorreoOrderByCreadoEnDesc(AccesoClinico.correo())
                .stream().filter(r -> r.getFechaDosis().equals(r.getVacuna().getProximaDosis()) && !vacunas.existsByIdVacunaOrigen(r.getVacuna().getIdVacuna()))
                .map(this::convertir).toList();
    }

    @Transactional
    public RecordatorioVacunaResponse marcarLeido(Long id) {
        if (!AccesoClinico.es("PROPIETARIO"))
            throw new AccessDeniedException("Solo el propietario marca sus recordatorios");
        RecordatorioVacunaEntity r = recordatorios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio no encontrado"));
        if (!AccesoClinico.esPropietario(r.getVacuna().getMascota()))
            throw new AccessDeniedException("Recordatorio de otro propietario");
        if (r.getLeidoEn() == null) r.setLeidoEn(LocalDateTime.now(clock.withZone(LIMA)));
        return convertir(r);
    }

    /** Recupera avisos aun si el servidor no estuvo activo en el instante exacto del umbral. */
    @Scheduled(cron = "${vetcare.recordatorios.cron:0 */5 * * * *}", zone = "${vetcare.recordatorios.zona:America/Lima}")
    @Transactional
    public void ejecutarAutomaticamente() { generarPendientes(); }

    @Transactional
    public int generarManualmente() {
        AccesoClinico.soloAdmin();
        return generarPendientes();
    }

    /** Se invoca en la misma transacción al crear o editar una programación. No envía correo. */
    @Transactional
    public int sincronizar(VacunaEntity vacuna) {
        if(vacuna.getProximaDosis()==null)return 0;
        String tipo=tipoPendiente(hoy(),vacuna.getProximaDosis());
        if(tipo==null)return 0;
        return recordatorios.crearSiFalta(vacuna.getIdVacuna(),vacuna.getProximaDosis(),tipo,
                LocalDateTime.now(clock.withZone(LIMA)));
    }

    static String tipoPendiente(LocalDate hoy,LocalDate fecha) {
        long dias=java.time.temporal.ChronoUnit.DAYS.between(hoy,fecha);
        return dias<0?"VENCIDA":dias==0?"HOY":dias==1?"DIA_1":dias<=7?"DIA_7":null;
    }

    private int generarPendientes() {
        int creados=0;
        for(VacunaEntity v:vacunas.findByProximaDosisIsNotNull())creados+=sincronizar(v);
        return creados;
    }
}
