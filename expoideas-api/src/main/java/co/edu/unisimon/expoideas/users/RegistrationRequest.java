package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.Password;
import co.edu.unisimon.expoideas.common.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registro público: solo correo institucional, contraseña y autorización de
 * datos. Toda cuenta nace como estudiante (el rol no se acepta desde el
 * cliente); el nombre y la adscripción se completan en el primer ingreso.
 */
public record RegistrationRequest(
        @NotBlank(message = "El correo institucional es obligatorio")
        @Email(message = "El formato del correo no es válido")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        @Pattern(
                regexp = ValidationPatterns.INSTITUTIONAL_EMAIL,
                message = ValidationPatterns.INSTITUTIONAL_EMAIL_MESSAGE)
        String email,

        @NotBlank(message = "La contraseña es obligatoria") @Password
        String password,

        @NotNull(message = "Debes indicar si autorizas el tratamiento de tus datos")
        @AssertTrue(message = "Debes autorizar el tratamiento de tus datos para registrarte")
        Boolean dataConsent) {}
