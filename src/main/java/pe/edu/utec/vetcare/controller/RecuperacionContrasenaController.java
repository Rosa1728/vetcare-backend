package pe.edu.utec.vetcare.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utec.vetcare.dto.request.*;
import pe.edu.utec.vetcare.service.RecuperacionContrasenaService;
import java.util.Map;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class RecuperacionContrasenaController {
 private final RecuperacionContrasenaService service;
 @PostMapping("/recuperar-contrasena")
 public ResponseEntity<?> solicitar(@Valid @RequestBody SolicitarRecuperacionRequest request){
  service.solicitar(request.correo());
  return ResponseEntity.accepted().body(Map.of("mensaje","Solicitud recibida para su procesamiento. Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña."));
 }
 @PostMapping("/restablecer-contrasena")
 public Map<String,String> restablecer(@Valid @RequestBody RestablecerContrasenaRequest request){
  service.restablecer(request.token(),request.contrasena());return Map.of("mensaje","Contraseña actualizada. Ya puedes iniciar sesión.");
 }
}
