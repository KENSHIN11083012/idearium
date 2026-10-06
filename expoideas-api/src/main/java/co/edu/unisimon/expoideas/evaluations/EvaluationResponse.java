package co.edu.unisimon.expoideas.evaluations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** La evaluación de un jurado: lo que eligió en cada criterio y la nota que resulta. */
public record EvaluationResponse(
        Integer id,
        Integer projectId,
        Integer jurorId,
        String juror,
        boolean absent,
        BigDecimal grade,
        GradeScale scale,
        List<ScoreResponse> scores,
        LocalDateTime updatedAt) {

    public record ScoreResponse(Integer criterionId, Integer levelId, BigDecimal score, String comment) {}

    public static EvaluationResponse from(Evaluation evaluation) {
        BigDecimal grade = evaluation.grade();
        return new EvaluationResponse(
                evaluation.getId(),
                evaluation.getProject().getId(),
                evaluation.getJuror().getId(),
                evaluation.getJuror().fullName(),
                evaluation.isAbsent(),
                grade,
                GradeScale.of(grade),
                evaluation.getScores().stream()
                        .sorted(Comparator.comparingInt(
                                score -> score.getCriterion().getPosition()))
                        .map(score -> new ScoreResponse(
                                score.getCriterion().getId(),
                                score.getLevel().getId(),
                                score.getScoreValue(),
                                score.getComment()))
                        .toList(),
                evaluation.getUpdatedAt());
    }
}
