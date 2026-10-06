package co.edu.unisimon.expoideas.jury;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Asignar un jurado a un proyecto, por el correo de su cuenta. */
public record JurorRequest(
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El formato del correo no es válido")
        String email) {}
