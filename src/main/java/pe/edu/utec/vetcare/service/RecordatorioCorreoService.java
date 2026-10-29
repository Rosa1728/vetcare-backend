package pe.edu.utec.vetcare.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.vetcare.entity.RecordatorioVacunaEntity;
import pe.edu.utec.vetcare.repository.RecordatorioVacunaRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordatorioCorreoService {
    private static final Logger log = LoggerFactory.getLogger(RecordatorioCorreoService.class);
    private final RecordatorioVacunaRepository recordatorios;
    private final ObjectProvider<JavaMailSender> mailSender;
    @Value("${vetcare.correo.habilitado:false}") private boolean habilitado;
    @Value("${vetcare.correo.remitente:no-reply@vetcare.test}") private String remitente;

    @Transactional
    public int enviarPendientes() {
        if (!habilitado) return 0;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Correo habilitado pero no existe JavaMailSender: configure MAIL_HOST");
            return 0;
        }
        int enviados = 0;
        List<RecordatorioVacunaEntity> pendientes = recordatorios.findByCorreoEnviadoEnIsNull();
        for (RecordatorioVacunaEntity r : pendientes) {
            if (!r.getFechaDosis().equals(r.getVacuna().getProximaDosis())) continue;
            String destinatario = r.getVacuna().getMascota().getPropietario().getUsuario().getCorreo();
            if (destinatario == null || destinatario.isBlank()) continue;
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(destinatario);
            mensaje.setSubject("VetCare - Recordatorio de vacuna de " + r.getVacuna().getMascota().getNombre());
            mensaje.setText("Hola. La vacuna " + r.getVacuna().getNombre() + " de "
                + r.getVacuna().getMascota().getNombre() + " tiene su proxima dosis el "
                + r.getFechaDosis() + ". Aviso: " + r.getTipo()
                + ". Contacta a tu veterinario para coordinar la atencion.");
            try {
                sender.send(mensaje);
                r.setCorreoEnviadoEn(LocalDateTime.now());
                enviados++;
            } catch (Exception ex) {
                // No imprimir destinatarios ni credenciales en los logs.
                log.warn("No fue posible enviar recordatorio {}: {}", r.getIdRecordatorio(), ex.getClass().getSimpleName());
            }
        }
        return enviados;
    }
}
