package pe.edu.utec.vetcare.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.CONFLICT)
public class EstadoVacunaInvalidoException extends RuntimeException {
    public EstadoVacunaInvalidoException(String message) { super(message); }
}
