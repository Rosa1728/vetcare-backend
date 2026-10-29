package pe.edu.utec.vetcare.validation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=ContrasenaSeguraValidator.class)
public @interface ContrasenaSegura {
 String message() default "Usa al menos 8 caracteres, mayúscula, minúscula, número y símbolo; evita contraseñas comunes (máximo 72 bytes)";
 Class<?>[] groups() default {};
 Class<? extends Payload>[] payload() default {};
}
