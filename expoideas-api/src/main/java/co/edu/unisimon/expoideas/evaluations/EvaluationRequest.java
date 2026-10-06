package co.edu.unisimon.expoideas.evaluations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lo que guarda un jurado: un nivel por criterio, o la marca de que el equipo
 * no asistió (entonces los niveles no cuentan). Que estén todos los criterios,
 * que cada nivel sea de su criterio y que haya observación por debajo de 3.0 lo
 * valida EvaluationService, porque depende de la rúbrica.
 */
public record EvaluationRequest(
        Boolean absent, @Valid List<@NotNull(message = "Elige un nivel para este criterio") ScoreRequest> scores) {

    public record ScoreRequest(
            @NotNull(message = "El criterio es obligatorio") Integer criterionId,

            @NotNull(message = "Elige un nivel para este criterio")
            Integer levelId,

            @Size(max = 500, message = "La observación no puede exceder 500 caracteres")
            String comment) {}

    public boolean isAbsent() {
        return Boolean.TRUE.equals(absent);
    }

    public List<ScoreRequest> scoresOrEmpty() {
        return scores == null ? List.of() : scores;
    }
}
