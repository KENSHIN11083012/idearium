package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.users.AccountDeletionRule;
import co.edu.unisimon.expoideas.users.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Los archivos de una cuenta se borran con ella, y una plantilla no puede
 * desaparecer mientras un entregable la ofrezca. Antes de eliminar la cuenta hay
 * que reemplazar o quitar sus plantillas.
 */
@Component
@RequiredArgsConstructor
class DeliverableTemplateAccountRule implements AccountDeletionRule {

    private final DeliverableTypeRepository typeRepository;

    @Override
    public Optional<String> reasonToKeep(User user) {
        if (typeRepository.existsByTemplateOwnerId(user.getId())) {
            return Optional.of(
                    "No puedes eliminar esta cuenta: subió plantillas de entregables. Reemplázalas o quítalas primero.");
        }
        return Optional.empty();
    }
}
