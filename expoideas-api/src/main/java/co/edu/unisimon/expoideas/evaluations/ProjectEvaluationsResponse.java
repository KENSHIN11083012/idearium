package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.users.User;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cómo va la evaluación de un proyecto, para el profesor del grupo y la
 * gestión: la nota (promedio de los jurados que ya calificaron, o null si
 * ninguno lo ha hecho), cada evaluación y quién falta.
 */
public record ProjectEvaluationsResponse(
        Integer projectId,
        BigDecimal grade,
        GradeScale scale,
        int jurors,
        List<EvaluationResponse> evaluations,
        List<PendingJuror> pending) {

    public record PendingJuror(Integer userId, String fullName) {

        static PendingJuror from(User juror) {
            return new PendingJuror(juror.getId(), juror.fullName());
        }
    }
}
