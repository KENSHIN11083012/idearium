package co.edu.unisimon.expoideas.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Una contraseña que cumple {@link ValidationPatterns#PASSWORD} y cabe en lo que
 * BCrypt alcanza a leer. Una vacía la deja pasar: de eso se encarga
 * {@code @NotBlank}, con su propio mensaje.
 */
@Documented
@Constraint(validatedBy = Password.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

    String message() default ValidationPatterns.PASSWORD_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Password, String> {

        private static final Pattern PATTERN = Pattern.compile(ValidationPatterns.PASSWORD);

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true;
            }
            return PATTERN.matcher(value).matches()
                    && value.getBytes(StandardCharsets.UTF_8).length <= ValidationPatterns.PASSWORD_MAX_BYTES;
        }
    }
}
