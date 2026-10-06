package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
import java.time.LocalDateTime;

/**
 * Una aprobación de cátedra. {@code projectId} y {@code projectTitle} son null
 * cuando la registró la gestión a mano; {@code approvedBy}, si esa cuenta ya no existe.
 */
public record TrackApprovalResponse(
        Integer id,
        Integer userId,
        Track track,
        Integer projectId,
        String projectTitle,
        String approvedBy,
        LocalDateTime approvedAt) {

    /** Requiere las relaciones cargadas (dentro de la transacción). */
    public static TrackApprovalResponse from(TrackApproval approval) {
        Project project = approval.getProject();
        return new TrackApprovalResponse(
                approval.getId(),
                approval.getUser().getId(),
                approval.getTrack(),
                project != null ? project.getId() : null,
                project != null ? project.getTitle() : null,
                approval.getApprovedBy() != null ? approval.getApprovedBy().fullName() : null,
                approval.getApprovedAt());
    }
}
