package co.edu.unisimon.expoideas.evaluations;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cómo va la nota de un proyecto, para los listados: el promedio de los jurados
 * que ya calificaron (null si ninguno) y cuántos faltan.
 *
 * @param jurors    jurados asignados ahora mismo
 * @param evaluated de esos, cuántos ya guardaron su evaluación
 */
public record ProjectGrade(BigDecimal grade, GradeScale scale, int jurors, int evaluated) {

    public static final ProjectGrade NONE = new ProjectGrade(null, null, 0, 0);

    static ProjectGrade of(List<Evaluation> counted, int jurors) {
        return GradeScale.average(counted.stream().map(Evaluation::grade).toList())
                .map(grade -> new ProjectGrade(grade, GradeScale.of(grade), jurors, counted.size()))
                .orElse(new ProjectGrade(null, null, jurors, 0));
    }
}
