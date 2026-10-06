package co.edu.unisimon.expoideas.presentations;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.notifications.PresentationScheduledEvent;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectPolicy;
import co.edu.unisimon.expoideas.users.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La agenda de sustentaciones. La escribe la gestión (ver SecurityConfig) y la
 * consultan el equipo y el profesor de cada proyecto. Programar o cambiar una
 * cita avisa por correo a todos ellos, después del commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationService {

    private final PresentationRepository presentationRepository;
    private final ProjectPolicy policy;
    private final ApplicationEventPublisher events;

    /** La sustentación del proyecto, si ya está programada y quien pregunta puede ver el proyecto. */
    @Transactional(readOnly = true)
    public Optional<PresentationResponse> get(Integer projectId, String email) {
        Project project = policy.findVisible(projectId, policy.account(email));
        return presentationRepository.findByProjectId(project.getId()).map(PresentationResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PresentationAgendaResponse> agenda(Integer editionId, Track track) {
        return presentationRepository.agenda(editionId, track).stream()
                .map(PresentationAgendaResponse::from)
                .toList();
    }

    /** Programa la sustentación o cambia la que había; en los dos casos avisa al equipo y al profesor. */
    @Transactional
    public PresentationResponse schedule(Integer projectId, PresentationRequest request) {
        Project project = policy.find(projectId);
        Presentation presentation = presentationRepository
                .findByProjectId(projectId)
                .orElseGet(() -> {
                    Presentation created = new Presentation();
                    created.setProject(project);
                    return created;
                });
        boolean rescheduled = presentation.getId() != null;
        presentation.setStartsAt(request.startsAt());
        presentation.setPlace(request.place().strip());
        presentation.setNotes(
                request.notes() == null || request.notes().isBlank()
                        ? null
                        : request.notes().strip());
        Presentation saved = presentationRepository.save(presentation);

        events.publishEvent(new PresentationScheduledEvent(
                recipients(project),
                project.getTitle(),
                saved.getStartsAt(),
                saved.getPlace(),
                saved.getNotes(),
                rescheduled));
        log.info(
                "Proyecto {}: sustentación {} para {}",
                projectId,
                rescheduled ? "cambiada" : "programada",
                saved.getStartsAt());
        return PresentationResponse.from(saved);
    }

    /** Quita la cita. No avisa: la gestión decide cómo comunicarlo. */
    @Transactional
    public void cancel(Integer projectId) {
        presentationRepository.findByProjectId(projectId).ifPresent(presentationRepository::delete);
    }

    /** El equipo aceptado y el profesor del grupo, sin repetir. */
    private static List<String> recipients(Project project) {
        List<String> emails = new ArrayList<>();
        for (User member : project.acceptedMembers()) {
            emails.add(member.getEmail());
        }
        String teacher = project.getTeacher().getEmail();
        if (emails.stream().noneMatch(email -> email.equalsIgnoreCase(teacher))) {
            emails.add(teacher);
        }
        return emails;
    }
}
