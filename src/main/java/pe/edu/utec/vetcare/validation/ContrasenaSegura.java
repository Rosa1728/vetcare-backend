package pe.edu.utec.vetcare.validation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=ContrasenaSeguraValidator.class)
public @interface ContrasenaSegura {
 String message() default "Revisa la contraseña.";
 Class<?>[] groups() default {};
 Class<? extends Payload>[] payload() default {};
}
