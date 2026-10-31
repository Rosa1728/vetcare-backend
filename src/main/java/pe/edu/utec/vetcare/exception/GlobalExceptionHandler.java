package pe.edu.utec.vetcare.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>> solicitudInvalida(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Throwable cause=ex;
        while(cause!=null) {
            if(cause instanceof BadRequestException bad)return ResponseEntity.badRequest().body(Map.of("error",bad.getMessage()));
            cause=cause.getCause();
        }
        return ResponseEntity.badRequest().body(Map.of("error","La solicitud contiene campos no permitidos o datos inválidos."));
    }

    @ExceptionHandler(CambioContrasenaInvalidoException.class)
    public ResponseEntity<?> cambioContrasena(CambioContrasenaInvalidoException ex) {
        return ResponseEntity.badRequest().body(Map.of("error",ex.getMessage(),"campos",ex.getCampos()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> integridad(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(java.util.Map.of("error", "No se pudo guardar o eliminar: el registro está relacionado con otros datos o contiene un valor duplicado."));
    }


    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String,String>> conflictoVersion(org.springframework.orm.ObjectOptimisticLockingFailureException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","El expediente cambió mientras editabas. Conserva tu texto y vuelve a cargarlo antes de guardar."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                campos.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage() == null
                        ? "Valor inválido" : fieldError.getDefaultMessage()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", "Error de validación");
        response.put("campos", campos);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acceso denegado"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(
            ResourceNotFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            BadRequestException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CitaHorarioOcupadoException.class)
    public ResponseEntity<Map<String, String>> handleCitaHorarioOcupado(
            CitaHorarioOcupadoException ex) {

        Map<String, String> response = Map.of("error", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(EstadoCitaInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleEstadoCitaInvalido(
            EstadoCitaInvalidoException ex) {

        Map<String, String> response = Map.of("error", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(AtencionNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleAtencionNoEncontrada(
            AtencionNoEncontradaException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}