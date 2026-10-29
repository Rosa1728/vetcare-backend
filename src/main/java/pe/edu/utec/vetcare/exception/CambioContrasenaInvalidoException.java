package pe.edu.utec.vetcare.exception;
import java.util.Map;
public class CambioContrasenaInvalidoException extends RuntimeException {
 private final Map<String,String> campos;
 public CambioContrasenaInvalidoException(String campo,String mensaje){super("Revisa los datos de la contraseña.");campos=Map.of(campo,mensaje);}
 public Map<String,String> getCampos(){return campos;}
}
