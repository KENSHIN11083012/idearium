package co.edu.unisimon.expoideas.projects;

/**
 * Una persona del equipo. El correo lo ven quienes ya están en el proyecto (y la
 * gestión), para que el líder sepa a quién invitó.
 */
public record MemberResponse(
        Integer userId, String fullName, String email, MemberRole teamRole, MembershipStatus status) {

    public static MemberResponse from(ProjectMember member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().fullName(),
                member.getUser().getEmail(),
                member.getTeamRole(),
                member.getStatus());
    }
}
