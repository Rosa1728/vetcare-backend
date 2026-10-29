package pe.edu.utec.vetcare.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${vetcare.recordatorios.zona:America/Lima}") private String zona;

    private LocalDate hoy() { return LocalDate.now(ZoneId.of(zona)); }

    private RecordatorioVacunaResponse convertir(RecordatorioVacunaEntity r) {
        VacunaEntity v = r.getVacuna();
        String mensaje = switch (r.getTipo()) {
            case "DIA_7" -> "La próxima dosis es dentro de 7 días";
            case "DIA_1" -> "La próxima dosis es mañana";
            case "HOY" -> "La próxima dosis corresponde hoy";
            default -> "La próxima dosis está vencida";
        };
        return new RecordatorioVacunaResponse(r.getIdRecordatorio(), v.getIdVacuna(),
                v.getMascota().getIdMascota(), v.getMascota().getNombre(), v.getNombre(),
                r.getFechaDosis(), r.getTipo(), mensaje, r.getCreadoEn(), r.getLeidoEn() != null);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioVacunaResponse> misRecordatorios() {
        if (!AccesoClinico.es("PROPIETARIO"))
            throw new AccessDeniedException("Solo el propietario consulta sus recordatorios");
        return recordatorios.findByVacuna_Mascota_Propietario_Usuario_CorreoOrderByCreadoEnDesc(AccesoClinico.correo())
                .stream().filter(r -> r.getFechaDosis().equals(r.getVacuna().getProximaDosis()))
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
        if (r.getLeidoEn() == null) r.setLeidoEn(LocalDateTime.now(ZoneId.of(zona)));
        return convertir(r);
    }

    /** Ejecución nocturna; la clave única protege frente a reintentos. */
    @Scheduled(cron = "${vetcare.recordatorios.cron:0 0 8 * * *}", zone = "${vetcare.recordatorios.zona:America/Lima}")
    @Transactional
    public void ejecutarAutomaticamente() { generarPendientes(); }

    @Transactional
    public int generarManualmente() {
        AccesoClinico.soloAdmin();
        return generarPendientes();
    }

    private int generarPendientes() {
        LocalDate hoy = hoy();
        int creados = 0;
        for (VacunaEntity v : vacunas.findByProximaDosisIsNotNull()) {
            LocalDate fecha = v.getProximaDosis();
            long dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, fecha);
            String tipo = dias == 7 ? "DIA_7" : dias == 1 ? "DIA_1" : dias == 0 ? "HOY" :
                    dias < 0 ? "VENCIDA" : null;
            if (tipo == null || recordatorios.existsByVacuna_IdVacunaAndFechaDosisAndTipo(v.getIdVacuna(), fecha, tipo))
                continue;
            RecordatorioVacunaEntity r = new RecordatorioVacunaEntity();
            r.setVacuna(v);
            r.setFechaDosis(fecha);
            r.setTipo(tipo);
            recordatorios.save(r);
            creados++;
        }
        return creados;
    }
}
