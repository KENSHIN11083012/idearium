package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.common.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Lo que dice quien acepta entrar a un equipo: el semestre que cursa. Es de esa
 * inscripción, no de la cuenta, así que se pregunta cada vez.
 */
public record InvitationAcceptanceRequest(
        @NotNull(message = ValidationPatterns.SEMESTER_MESSAGE)
        @Min(value = ValidationPatterns.MIN_SEMESTER, message = ValidationPatterns.SEMESTER_MESSAGE)
        @Max(value = ValidationPatterns.MAX_SEMESTER, message = ValidationPatterns.SEMESTER_MESSAGE)
        Integer semester) {}
