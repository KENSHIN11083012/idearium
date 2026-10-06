package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.Password;
import jakarta.validation.constraints.NotBlank;

/** Contraseña que la gestión pone a otra cuenta; queda como temporal. */
public record PasswordResetRequest(
        @NotBlank(message = "La nueva contraseña no puede estar vacía") @Password
        String newPassword,

        @NotBlank(message = "La confirmación de contraseña no puede estar vacía")
        String confirmPassword) {}
