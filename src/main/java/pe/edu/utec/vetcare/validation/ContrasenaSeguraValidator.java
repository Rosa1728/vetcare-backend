package pe.edu.utec.vetcare.validation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura,String> {
 public boolean isValid(String value,ConstraintValidatorContext context){String error=PoliticaContrasena.error(value);
  if(error.isEmpty())return true;
  context.disableDefaultConstraintViolation();
  context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
  return false;}
}
