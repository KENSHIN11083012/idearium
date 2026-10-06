package co.edu.unisimon.expoideas.users;

import jakarta.validation.constraints.NotBlank;

/** El token del enlace de verificación que llegó al correo. */
public record EmailVerificationRequest(
        @NotBlank(message = "Falta el token del enlace") String token) {}
