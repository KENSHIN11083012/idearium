package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.users.PersonalDataSource;
import co.edu.unisimon.expoideas.users.User;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lo que este módulo guarda de una persona: los equipos en los que está o a
 * los que la invitaron, los proyectos que tiene a cargo como profesor y las
 * cátedras que tiene aprobadas.
 */
@Component
@RequiredArgsConstructor
class ProjectPersonalData implements PersonalDataSource {

    private final ProjectRepository projectRepository;
    private final TrackApprovalRepository approvalRepository;

    record Team(
            String project,
            String edition,
            Track track,
            MemberRole teamRole,
            MembershipStatus status,
            Integer semester,
            String phone,
            LocalDateTime invitedAt,
            LocalDateTime respondedAt,
            ProjectResult result) {}

    record TaughtProject(String project, String edition, Track track, LocalDateTime registeredAt) {}

    /** @param project el proyecto con el que la aprobó, o null si la dio por aprobada la gestión */
    record Approval(Track track, String project, LocalDateTime approvedAt) {}

    @Override
    public Map<String, List<?>> dataOf(User user) {
        List<Team> teams = projectRepository.findAllOfMember(user.getId()).stream()
                .map(project -> {
                    ProjectMember member = project.memberOf(user).orElseThrow();
                    return new Team(
                            project.getTitle(),
                            project.getEdition().getName(),
                            project.getTrack(),
                            member.getTeamRole(),
                            member.getStatus(),
                            member.getSemester(),
                            member.getPhone(),
                            member.getInvitedAt(),
                            member.getRespondedAt(),
                            project.getResult());
                })
                .toList();
        List<TaughtProject> taught = projectRepository.findByTeacherIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(project -> new TaughtProject(
                        project.getTitle(), project.getEdition().getName(), project.getTrack(), project.getCreatedAt()))
                .toList();
        List<Approval> approvals = approvalRepository.findByUserIdOrderByTrackAsc(user.getId()).stream()
                .map(approval -> new Approval(
                        approval.getTrack(),
                        approval.getProject() != null ? approval.getProject().getTitle() : null,
                        approval.getApprovedAt()))
                .toList();

        Map<String, List<?>> sections = new LinkedHashMap<>();
        sections.put("teams", teams);
        sections.put("projectsAsTeacher", taught);
        sections.put("trackApprovals", approvals);
        return sections;
    }
}
