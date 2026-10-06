package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.editions.Track;
import java.math.BigDecimal;
import java.util.List;

/** Una versión de la rúbrica de una cátedra, con sus criterios y los niveles de cada uno en orden. */
public record RubricResponse(
        Integer id, Track track, int version, String name, String description, List<CriterionResponse> criteria) {

    public record CriterionResponse(
            Integer id, int position, String name, String shortName, List<LevelResponse> levels) {}

    public record LevelResponse(Integer id, int position, String label, BigDecimal score, String description) {}

    public static RubricResponse from(Rubric rubric) {
        return new RubricResponse(
                rubric.getId(),
                rubric.getTrack(),
                rubric.getVersion(),
                rubric.getName(),
                rubric.getDescription(),
                rubric.getCriteria().stream().map(RubricResponse::criterion).toList());
    }

    private static CriterionResponse criterion(RubricCriterion criterion) {
        return new CriterionResponse(
                criterion.getId(),
                criterion.getPosition(),
                criterion.getName(),
                criterion.getShortName(),
                criterion.getLevels().stream()
                        .map(level -> new LevelResponse(
                                level.getId(),
                                level.getPosition(),
                                level.getLabel(),
                                level.getScore(),
                                level.getDescription()))
                        .toList());
    }
}
