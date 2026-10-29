package pe.edu.utec.vetcare.config;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.utec.vetcare.service.RecordatorioCorreoService;

@Component
@RequiredArgsConstructor
public class RecordatorioCorreoScheduler {
    private final RecordatorioCorreoService correo;
    @Scheduled(cron = "${vetcare.correo.cron:0 5 8 * * *}", zone = "${vetcare.recordatorios.zona:America/Lima}")
    public void ejecutar() { correo.enviarPendientes(); }
}
