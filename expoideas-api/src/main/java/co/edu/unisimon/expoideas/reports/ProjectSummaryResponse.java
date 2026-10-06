package co.edu.unisimon.expoideas.reports;

import co.edu.unisimon.expoideas.deliverables.DeliverableProgressService.Progress;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.evaluations.GradeScale;
import co.edu.unisimon.expoideas.evaluations.ProjectGrade;
import co.edu.unisimon.expoideas.projects.MembershipStatus;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un proyecto en un listado de gestión: lo justo para reconocerlo y ver cómo
 * va, sin su equipo completo ni sus archivos.
 *
 * @param grade     promedio de los jurados que ya calificaron, o null si ninguno
 * @param jurors    jurados asignados
 * @param evaluated cuántos de ellos ya calificaron
 */
public record ProjectSummaryResponse(
        Integer id,
        Integer editionId,
        String edition,
        Track track,
        String title,
        String sector,
        String prototypeType,
        String teacher,
        String leader,
        int members,
        int minMembers,
        int requiredDeliverables,
        int deliveredDeliverables,
        BigDecimal grade,
        GradeScale scale,
        int jurors,
        int evaluated,
        ProjectResult result,
        LocalDateTime createdAt) {

    public static ProjectSummaryResponse of(Project project, Progress progress, ProjectGrade grade) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getEdition().getId(),
                project.getEdition().getName(),
                project.getTrack(),
                project.getTitle(),
                project.getSector().getName(),
                project.getPrototypeType() != null ? project.getPrototypeType().getName() : null,
                project.getTeacher().fullName(),
                project.leader().getUser().fullName(),
                (int) project.getMembers().stream()
                        .filter(member -> member.getStatus() == MembershipStatus.ACCEPTED)
                        .count(),
                project.trackSettings().getMinMembers(),
                progress.required(),
                progress.delivered(),
                grade.grade(),
                grade.scale(),
                grade.jurors(),
                grade.evaluated(),
                project.getResult(),
                project.getCreatedAt());
    }
}
