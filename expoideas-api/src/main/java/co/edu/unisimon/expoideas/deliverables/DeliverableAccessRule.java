package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.files.PrivateFileAccessRule;
import co.edu.unisimon.expoideas.files.StoredFile;
import co.edu.unisimon.expoideas.projects.ProjectPolicy;
import co.edu.unisimon.expoideas.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los entregables son archivos privados: los abre quien puede ver el proyecto
 * (el equipo, su profesor, la gestión y los jurados asignados), según decide
 * {@link ProjectPolicy}; no quien solo está invitado al equipo y todavía no ha
 * aceptado. El propietario del archivo ya tiene permiso por serlo.
 */
@Component
@RequiredArgsConstructor
class DeliverableAccessRule implements PrivateFileAccessRule {

    private final DeliverableRepository deliverableRepository;
    private final UserRepository userRepository;
    private final ProjectPolicy policy;

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(StoredFile file, String email) {
        return deliverableRepository
                .findByFileId(file.getId())
                .map(Deliverable::getProject)
                .flatMap(project ->
                        userRepository.findByEmail(email).map(viewer -> policy.canSeeDeliverables(project, viewer)))
                .orElse(false);
    }
}
