package co.edu.unisimon.expoideas.jury;

import co.edu.unisimon.expoideas.evaluations.EvaluatorRule;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.users.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Califican un proyecto los jurados que la gestión le asignó. */
@Component
@RequiredArgsConstructor
class JuryEvaluatorRule implements EvaluatorRule {

    private final JuryAssignmentRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isEvaluator(Project project, User user) {
        return assignmentRepository.existsByProjectIdAndUserId(project.getId(), user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> evaluatorsOf(Project project) {
        return assignmentRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()).stream()
                .map(JuryAssignment::getUser)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, List<User>> evaluatorsByProject(Collection<Project> projects) {
        if (projects.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = projects.stream().map(Project::getId).toList();
        Map<Integer, List<User>> byProject = new LinkedHashMap<>();
        for (JuryAssignment assignment : assignmentRepository.findByProjectIdInOrderByCreatedAtAsc(ids)) {
            byProject
                    .computeIfAbsent(assignment.getProject().getId(), id -> new ArrayList<>())
                    .add(assignment.getUser());
        }
        return byProject;
    }
}
