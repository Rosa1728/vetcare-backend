package pe.edu.utec.vetcare.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utec.vetcare.entity.RecuperacionContrasenaEntity;
import pe.edu.utec.vetcare.exception.BadRequestException;
import pe.edu.utec.vetcare.repository.*;
import pe.edu.utec.vetcare.validation.PoliticaContrasena;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class RecuperacionContrasenaService {
 private final RecuperacionContrasenaRepository solicitudes;
 private final UsuarioRepository usuarios;
 private final PasswordEncoder encoder;
 private final ObjectProvider<JavaMailSender> correo;
 private final EntityManager em;
 private final Clock clock;
 @Value("${vetcare.recuperacion.habilitada:false}") private boolean activa;
 @Value("${vetcare.recuperacion.correo-habilitado:${vetcare.correo.habilitado:false}}") private boolean correoActivo;
 @Value("${vetcare.correo.remitente:no-reply@vetcare.test}") private String remitente;
 @Value("${vetcare.recuperacion.url-publica:http://localhost:5173}") private String urlPublica;
 private static final SecureRandom RANDOM=new SecureRandom();
 private void activa(){if(!activa)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"La recuperación todavía no está habilitada");}
 @Transactional
 public void solicitar(String email){
  activa();if(!correoActivo||correo.getIfAvailable()==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"El correo de recuperación no está configurado");
  String normalized=email.trim().toLowerCase(Locale.ROOT);Instant now=clock.instant();
  if(solicitudes.existsByCorreoAndCreadoEnAfter(normalized,now.minusSeconds(60)))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Espera un minuto antes de solicitar nuevamente");
  // Igual procesamiento público para correos registrados y desconocidos: no consulta cuentas.
  var request=new RecuperacionContrasenaEntity();request.setCorreo(normalized);request.setCreadoEn(now);solicitudes.save(request);
 }
 @Scheduled(cron="${vetcare.recuperacion.cron:0 * * * * *}")
 @Transactional
 public void procesarPendientes(){
  if(!activa||!correoActivo||correo.getIfAvailable()==null)return;
  for(var candidate:solicitudes.findTop10ByEstadoOrderByCreadoEnAsc("PENDIENTE")){
   var r=solicitudes.bloquear(candidate.getId()).orElseThrow();em.refresh(r,LockModeType.PESSIMISTIC_WRITE);
   if(!"PENDIENTE".equals(r.getEstado()))continue;
   if(r.getUsadoEn()!=null){r.setEstado("PROCESADA");continue;}
   var u=usuarios.findByCorreo(r.getCorreo()).orElse(null);
   if(u==null){r.setEstado("PROCESADA");continue;}
   byte[] entropy=new byte[32];RANDOM.nextBytes(entropy);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
   var message=new SimpleMailMessage();message.setFrom(remitente);message.setTo(r.getCorreo());message.setSubject("VetCare — Recuperar tu cuenta");
   message.setText("Para crear una nueva contraseña, abre el siguiente enlace. Es válido durante 30 minutos y solo puede utilizarse una vez.\n"+urlPublica.replaceAll("/$","")+"/restablecer-contrasena#token="+token+"\nSi no solicitaste este cambio, ignora este mensaje.");
   r.setIntentos(r.getIntentos()+1);r.setIdUsuario(u.getIdUsuario());
   try{
    correo.getIfAvailable().send(message);
    r.setTokenHash(hash(token));r.setVenceEn(clock.instant().plus(Duration.ofMinutes(30)));r.setEstado("PROCESADA");
   }catch(org.springframework.mail.MailException ex){
    // No imprimir destinatarios, contraseñas ni enlaces. La solicitud queda pendiente hasta 3 intentos.
    r.setTokenHash(null);r.setVenceEn(null);if(r.getIntentos()>=3)r.setEstado("ERROR");
   }
  }
 }
 @Transactional
 public void restablecer(String token,String password){
  activa();PoliticaContrasena.validar(password);
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw invalido();
  var initial=solicitudes.findByTokenHash(hash(token)).orElseThrow(this::invalido);
  if(initial.getIdUsuario()==null)throw invalido();
  var u=usuarios.findById(initial.getIdUsuario()).orElseThrow(this::invalido);
  // Serializa cambios de contraseña de la misma cuenta, incluso usando enlaces diferentes.
  em.refresh(u,LockModeType.PESSIMISTIC_WRITE);
  var r=solicitudes.bloquear(initial.getId()).orElseThrow(this::invalido);em.refresh(r,LockModeType.PESSIMISTIC_WRITE);Instant now=clock.instant();
  if(r.getUsadoEn()!=null||r.getVenceEn()==null||!r.getVenceEn().isAfter(now)||!u.getCorreo().equals(r.getCorreo()))throw invalido();
  u.setContrasena(encoder.encode(password));usuarios.saveAndFlush(u);
  solicitudes.invalidar(u.getIdUsuario(),now);
 }
 private BadRequestException invalido(){return new BadRequestException("El enlace no es válido, ya fue utilizado o venció. Solicita uno nuevo.");}
 private static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 no disponible");}}
}
