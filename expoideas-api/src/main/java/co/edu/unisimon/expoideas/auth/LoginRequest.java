package co.edu.unisimon.expoideas.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de login. Solo se exige que lleguen: el formato del correo no se
 * valida aquí para no dar pistas distintas de "Credenciales inválidas".
 */
public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {}
