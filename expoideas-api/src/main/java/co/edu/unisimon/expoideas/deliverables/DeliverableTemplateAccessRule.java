package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.files.PrivateFileAccessRule;
import co.edu.unisimon.expoideas.files.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * La plantilla de un entregable la descarga cualquiera con sesión: el equipo la
 * diligencia y el profesor la consulta. Sin sesión sigue siendo privada.
 */
@Component
@RequiredArgsConstructor
class DeliverableTemplateAccessRule implements PrivateFileAccessRule {

    private final DeliverableTypeRepository typeRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(StoredFile file, String email) {
        return email != null && typeRepository.existsByTemplateId(file.getId());
    }
}
