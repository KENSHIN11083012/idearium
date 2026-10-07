package co.edu.unisimon.expoideas.projects;

import jakarta.validation.constraints.Size;

/**
 * Invitación a un compañero. Se dice quién de una de dos maneras: con su correo
 * institucional, o con su cuenta si el líder lo eligió en la búsqueda por nombre
 * ({@code GET /projects/{id}/invitation-candidates}). Si llegan las dos, manda la
 * cuenta; si no llega ninguna, el servicio lo dice en el campo.
 */
public record InvitationRequest(
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String email,

        Integer userId) {}
