package co.edu.unisimon.expoideas.presentations;

import java.time.LocalDateTime;

/** Una cita de la agenda de la gestión: lo justo para reconocer el proyecto. */
public record PresentationAgendaResponse(
        Integer id,
        Integer projectId,
        String projectTitle,
        String leader,
        String teacher,
        LocalDateTime startsAt,
        String place,
        String notes) {

    /** Requiere el proyecto con su equipo y su profesor cargados. */
    public static PresentationAgendaResponse from(Presentation presentation) {
        return new PresentationAgendaResponse(
                presentation.getId(),
                presentation.getProject().getId(),
                presentation.getProject().getTitle(),
                presentation.getProject().leader().getUser().fullName(),
                presentation.getProject().getTeacher().fullName(),
                presentation.getStartsAt(),
                presentation.getPlace(),
                presentation.getNotes());
    }
}
