package co.edu.unisimon.expoideas.presentations;

import java.time.LocalDateTime;

/** La sustentación de un proyecto, como la ven el equipo, el profesor y la gestión. */
public record PresentationResponse(
        Integer id, Integer projectId, LocalDateTime startsAt, String place, String notes, LocalDateTime updatedAt) {

    public static PresentationResponse from(Presentation presentation) {
        return new PresentationResponse(
                presentation.getId(),
                presentation.getProject().getId(),
                presentation.getStartsAt(),
                presentation.getPlace(),
                presentation.getNotes(),
                presentation.getUpdatedAt());
    }
}
