package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
import java.time.LocalDateTime;

/** Invitación sin responder, como la ve quien la recibió. */
public record InvitationResponse(
        Integer id,
        Integer projectId,
        String projectTitle,
        String edition,
        Track track,
        String leader,
        LocalDateTime invitedAt) {

    public static InvitationResponse from(ProjectMember invitation) {
        Project project = invitation.getProject();
        return new InvitationResponse(
                invitation.getId(),
                project.getId(),
                project.getTitle(),
                project.getEdition().getName(),
                project.getTrack(),
                project.leader().getUser().fullName(),
                invitation.getInvitedAt());
    }
}
