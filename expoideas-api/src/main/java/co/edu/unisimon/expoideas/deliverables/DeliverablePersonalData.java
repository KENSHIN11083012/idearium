package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.users.PersonalDataSource;
import co.edu.unisimon.expoideas.users.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Lo que este módulo guarda de una persona: los entregables que ella subió a sus proyectos. */
@Component
@RequiredArgsConstructor
class DeliverablePersonalData implements PersonalDataSource {

    private final DeliverableRepository deliverableRepository;

    /** @param file el nombre del archivo, o null si es un enlace; {@code url}, al revés */
    record Upload(String project, String deliverable, String file, String url, LocalDateTime uploadedAt) {}

    @Override
    public Map<String, List<?>> dataOf(User user) {
        List<Upload> uploads = deliverableRepository.findByUploadedByIdOrderByUploadedAtAsc(user.getId()).stream()
                .map(deliverable -> new Upload(
                        deliverable.getProject().getTitle(),
                        deliverable.getType().getName(),
                        deliverable.getFile() != null ? deliverable.getFile().getOriginalName() : null,
                        deliverable.getUrl(),
                        deliverable.getUploadedAt()))
                .toList();
        return Map.of("deliverables", uploads);
    }
}
