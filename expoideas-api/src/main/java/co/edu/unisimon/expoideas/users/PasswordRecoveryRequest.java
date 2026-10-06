package co.edu.unisimon.expoideas.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El correo de la cuenta cuya contraseña se quiere recuperar. */
public record PasswordRecoveryRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El formato del correo no es válido")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String email) {}
