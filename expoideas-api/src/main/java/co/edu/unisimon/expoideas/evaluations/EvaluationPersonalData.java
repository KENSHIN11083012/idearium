package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.users.PersonalDataSource;
import co.edu.unisimon.expoideas.users.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lo que este módulo guarda de una persona: las evaluaciones que hizo como
 * jurado, con lo que escribió en cada criterio. La nota que recibió su equipo
 * no es suya sino del proyecto, y la ve en la ficha cuando se publica.
 */
@Component
@RequiredArgsConstructor
class EvaluationPersonalData implements PersonalDataSource {

    private final EvaluationRepository evaluationRepository;

    record Given(String project, boolean absent, BigDecimal grade, LocalDateTime updatedAt, List<Score> scores) {}

    record Score(String criterion, String level, BigDecimal value, String comment) {}

    @Override
    public Map<String, List<?>> dataOf(User user) {
        List<Given> evaluations = evaluationRepository.findByJurorIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(evaluation -> new Given(
                        evaluation.getProject().getTitle(),
                        evaluation.isAbsent(),
                        evaluation.grade(),
                        evaluation.getUpdatedAt(),
                        evaluation.getScores().stream()
                                .map(score -> new Score(
                                        score.getCriterion().getShortName(),
                                        score.getLevel().getLabel(),
                                        score.getScoreValue(),
                                        score.getComment()))
                                .toList()))
                .toList();
        return Map.of("evaluations", evaluations);
    }
}
