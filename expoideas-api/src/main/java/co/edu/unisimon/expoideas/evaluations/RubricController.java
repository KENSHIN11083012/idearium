package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.editions.Track;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las rúbricas. Las define MacondoLab y cualquiera con sesión lee la vigente de
 * cada cátedra; la de un proyecto, que es la de su edición, quien ve el proyecto.
 */
@RestController
@RequiredArgsConstructor
public class RubricController {

    private final EvaluationService evaluationService;

    /** La versión vigente: con la que nacerá la próxima edición. 404 si la cátedra no tiene rúbrica. */
    @GetMapping("/api/v1/rubrics/{track}")
    public RubricResponse rubric(@PathVariable Track track) {
        return evaluationService.rubric(track);
    }

    /** La rúbrica con la que se califica ese proyecto. 404 si no lo ve o su edición no tiene rúbrica. */
    @GetMapping("/api/v1/projects/{projectId}/rubric")
    public RubricResponse rubricOfProject(@PathVariable Integer projectId, Principal principal) {
        return evaluationService.rubricOf(projectId, principal.getName());
    }
}
