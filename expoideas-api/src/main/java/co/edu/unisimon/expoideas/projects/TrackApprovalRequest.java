package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
import jakarta.validation.constraints.NotNull;

/** Aprobación manual de una cátedra para una persona. */
public record TrackApprovalRequest(
        @NotNull(message = "La persona es obligatoria") Integer userId,
        @NotNull(message = "La cátedra es obligatoria") Track track) {}
