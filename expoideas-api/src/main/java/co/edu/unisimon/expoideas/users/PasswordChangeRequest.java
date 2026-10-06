package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.Password;
import jakarta.validation.constraints.NotBlank;

/** Cambio de la propia contraseña: exige la actual. */
public record PasswordChangeRequest(
        @NotBlank(message = "Debes proporcionar la contraseña actual.")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña no puede estar vacía") @Password
        String newPassword,

        @NotBlank(message = "La confirmación de contraseña no puede estar vacía")
        String confirmPassword) {}
