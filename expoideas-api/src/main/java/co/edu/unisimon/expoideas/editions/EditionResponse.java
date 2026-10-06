package co.edu.unisimon.expoideas.editions;

import java.time.LocalDate;
import java.util.List;

/**
 * Una edición con su configuración. {@code registrationOpen} y
 * {@code submissionOpen} dicen qué se puede hacer hoy, para que el cliente no
 * tenga que comparar fechas por su cuenta.
 */
public record EditionResponse(
        Integer id,
        String name,
        LocalDate registrationOpensOn,
        LocalDate registrationClosesOn,
        LocalDate submissionClosesOn,
        boolean registrationOpen,
        boolean submissionOpen,
        List<TrackSettingsResponse> tracks) {

    public static EditionResponse from(Edition edition, LocalDate today) {
        return new EditionResponse(
                edition.getId(),
                edition.getName(),
                edition.getRegistrationOpensOn(),
                edition.getRegistrationClosesOn(),
                edition.getSubmissionClosesOn(),
                edition.isRegistrationOpenOn(today),
                edition.isSubmissionOpenOn(today),
                edition.getTracks().stream().map(TrackSettingsResponse::from).toList());
    }
}
