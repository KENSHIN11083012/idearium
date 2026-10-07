package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.EditionTrack;
import co.edu.unisimon.expoideas.editions.Track;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Un proyecto con su equipo. Lleva los límites del grupo, si siguen abiertas la
 * inscripción (equipo y datos) y las entregas, y el día en que cierra cada una,
 * para que el cliente sepa qué ofrecer y cuánto falta sin consultar la edición.
 *
 * @param leaderPhone el celular de contacto del líder, solo para quien puede verlo
 *     ({@link ProjectPolicy#canSeeContact}); para los demás, null
 */
public record ProjectResponse(
        Integer id,
        Integer editionId,
        String edition,
        Track track,
        String title,
        String summary,
        Integer sectorId,
        String sector,
        Integer prototypeTypeId,
        String prototypeType,
        Integer teacherId,
        String teacher,
        boolean registrationOpen,
        boolean submissionOpen,
        LocalDate registrationClosesOn,
        LocalDate submissionClosesOn,
        int minMembers,
        int maxMembers,
        List<MemberResponse> members,
        String leaderPhone,
        ProjectResult result,
        LocalDateTime createdAt) {

    /** Sin el celular del líder: es lo que ve quien no tiene por qué llamarlo, como un jurado. */
    public static ProjectResponse from(Project project, LocalDate today) {
        return from(project, today, false);
    }

    /** @param withContact si lleva el celular del líder */
    public static ProjectResponse from(Project project, LocalDate today, boolean withContact) {
        EditionTrack settings = project.trackSettings();
        return new ProjectResponse(
                project.getId(),
                project.getEdition().getId(),
                project.getEdition().getName(),
                project.getTrack(),
                project.getTitle(),
                project.getSummary(),
                project.getSector().getId(),
                project.getSector().getName(),
                project.getPrototypeType() != null ? project.getPrototypeType().getId() : null,
                project.getPrototypeType() != null ? project.getPrototypeType().getName() : null,
                project.getTeacher().getId(),
                project.getTeacher().fullName(),
                project.getEdition().isRegistrationOpenOn(today),
                project.getEdition().isSubmissionOpenOn(today),
                project.getEdition().getRegistrationClosesOn(),
                project.getEdition().getSubmissionClosesOn(),
                settings.getMinMembers(),
                settings.getMaxMembers(),
                project.getMembers().stream().map(MemberResponse::from).toList(),
                withContact ? project.leader().getPhone() : null,
                project.getResult(),
                project.getCreatedAt());
    }
}
