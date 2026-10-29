package pe.edu.utec.vetcare.validation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura,String> {
 public boolean isValid(String value,ConstraintValidatorContext context){return PoliticaContrasena.segura(value);}
}
