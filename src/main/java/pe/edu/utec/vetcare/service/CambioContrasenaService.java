package pe.edu.utec.vetcare.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utec.vetcare.dto.request.CambiarContrasenaRequest;
import pe.edu.utec.vetcare.exception.CambioContrasenaInvalidoException;
import pe.edu.utec.vetcare.exception.ResourceNotFoundException;
import pe.edu.utec.vetcare.repository.UsuarioRepository;
import pe.edu.utec.vetcare.validation.PoliticaContrasena;

@Service @RequiredArgsConstructor
public class CambioContrasenaService {
 private final UsuarioRepository usuarios;
 private final PasswordEncoder encoder;
 private final EntityManager em;
 private final Clock clock;
 private record Intentos(Instant inicio,int cantidad) {}
 private final ConcurrentHashMap<Long,Intentos> intentos = new ConcurrentHashMap<>();
 private void registrarIntento(Long id) {
  Instant now=clock.instant();
  intentos.entrySet().removeIf(e->!e.getValue().inicio().plusSeconds(60).isAfter(now));
  if(!intentos.containsKey(id)&&intentos.size()>=5000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Espera un minuto antes de intentar nuevamente.");
  intentos.compute(id,(key,value)->{
   if(value==null||!value.inicio().plusSeconds(60).isAfter(now))return new Intentos(now,1);
   if(value.cantidad()>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Espera un minuto antes de intentar nuevamente.");
   return new Intentos(value.inicio(),value.cantidad()+1);
  });
 }
 @Transactional
 public void cambiar(Long id,CambiarContrasenaRequest request) {
  registrarIntento(id);
  var u=usuarios.findById(id).orElseThrow(()->new ResourceNotFoundException("Usuario autenticado no encontrado"));
  em.refresh(u,LockModeType.PESSIMISTIC_WRITE);
  if(request.contrasenaActual().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || !encoder.matches(request.contrasenaActual(),u.getContrasena()))throw new CambioContrasenaInvalidoException("contrasenaActual","La contraseña actual es incorrecta.");
  if(!PoliticaContrasena.segura(request.nuevaContrasena()))throw new CambioContrasenaInvalidoException("nuevaContrasena","Usa al menos 8 caracteres con mayúscula, minúscula, número y símbolo. Evita contraseñas comunes; si es demasiado larga, elige una más corta.");
  if(!request.nuevaContrasena().equals(request.confirmarContrasena()))throw new CambioContrasenaInvalidoException("confirmarContrasena","Las contraseñas no coinciden.");
  if(encoder.matches(request.nuevaContrasena(),u.getContrasena()))throw new CambioContrasenaInvalidoException("nuevaContrasena","Elige una contraseña diferente de la actual.");
  u.setContrasena(encoder.encode(request.nuevaContrasena()));usuarios.saveAndFlush(u);
  intentos.remove(id);
 }
}
