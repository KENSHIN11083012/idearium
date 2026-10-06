package co.edu.unisimon.expoideas.jury;

import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectVisibilityRule;
import co.edu.unisimon.expoideas.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Un jurado asignado ve el proyecto que le tocó, con sus entregables. */
@Component
@RequiredArgsConstructor
class JuryVisibilityRule implements ProjectVisibilityRule {

    private final JuryAssignmentRepository assignmentRepository;

    @Override
    public boolean canView(Project project, User viewer) {
        return assignmentRepository.existsByProjectIdAndUserId(project.getId(), viewer.getId());
    }
}
