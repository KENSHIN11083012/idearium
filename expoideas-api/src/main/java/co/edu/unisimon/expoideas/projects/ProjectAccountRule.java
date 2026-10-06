package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.users.AccountDeletionRule;
import co.edu.unisimon.expoideas.users.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una cuenta que está en el equipo de un proyecto o que es el docente de uno no
 * se elimina: el proyecto quedaría sin responsable o sin historia. Primero hay
 * que sacarla del equipo o cambiar el docente.
 */
@Component
@RequiredArgsConstructor
class ProjectAccountRule implements AccountDeletionRule {

    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> reasonToKeep(User user) {
        if (projectRepository.existsByTeacherId(user.getId())) {
            return Optional.of("No puedes eliminar esta cuenta: es el profesor de al menos un proyecto inscrito.");
        }
        if (projectRepository.isOnSomeTeam(user.getId())) {
            return Optional.of(
                    "No puedes eliminar esta cuenta: está en el equipo de al menos un proyecto. Sácala del equipo primero.");
        }
        return Optional.empty();
    }
}
