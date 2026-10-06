package co.edu.unisimon.expoideas.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invitación a un compañero, por su correo institucional. */
public record InvitationRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String email) {}
