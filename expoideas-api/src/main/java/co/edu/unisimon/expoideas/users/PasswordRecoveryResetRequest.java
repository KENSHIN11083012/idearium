package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.Password;
import jakarta.validation.constraints.NotBlank;

/** La contraseña nueva de quien abrió el enlace de recuperación. */
public record PasswordRecoveryResetRequest(
        @NotBlank(message = "Falta el token del enlace") String token,

        @NotBlank(message = "La nueva contraseña no puede estar vacía") @Password
        String newPassword,

        @NotBlank(message = "Confirma la nueva contraseña") String confirmPassword) {}
