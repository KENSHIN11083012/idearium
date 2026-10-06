package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.users.AccountDeletionRule;
import co.edu.unisimon.expoideas.users.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una cuenta que ya calificó proyectos no se elimina: con ella se irían las
 * notas que puso, y son evidencia de la evaluación.
 */
@Component
@RequiredArgsConstructor
class EvaluationAccountRule implements AccountDeletionRule {

    private final EvaluationRepository evaluationRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> reasonToKeep(User user) {
        if (evaluationRepository.existsByJurorId(user.getId())) {
            return Optional.of("No puedes eliminar esta cuenta: ya calificó al menos un proyecto como jurado.");
        }
        return Optional.empty();
    }
}
