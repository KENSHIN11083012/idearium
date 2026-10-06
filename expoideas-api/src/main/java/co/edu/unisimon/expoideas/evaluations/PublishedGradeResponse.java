package co.edu.unisimon.expoideas.evaluations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que el equipo ve de su evaluación una vez publicada: la nota final (el
 * promedio de los jurados) con su nivel, cuántos jurados calificaron y, por
 * criterio, las observaciones, sin decir qué jurado escribió cada una.
 */
public record PublishedGradeResponse(
        Integer projectId,
        BigDecimal grade,
        GradeScale scale,
        int evaluated,
        LocalDateTime publishedAt,
        List<CriterionFeedback> criteria) {

    /** @param comments las observaciones de los jurados en ese criterio, en el orden en que calificaron */
    public record CriterionFeedback(Integer criterionId, int position, String name, List<String> comments) {}
}
