package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.ForbiddenActionException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.files.FileService;
import co.edu.unisimon.expoideas.files.FileVisibility;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectDeletedEvent;
import co.edu.unisimon.expoideas.projects.ProjectPolicy;
import co.edu.unisimon.expoideas.users.User;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Los archivos que un equipo sube, y los enlaces que registra, para los
 * entregables de su cátedra.
 *
 * <p>Sube el equipo, no el profesor, y solo hasta el cierre de entregas (el de la
 * edición o el propio del entregable, si lo tiene). Los archivos son privados:
 * los abren el equipo, su profesor y la gestión (ver {@link DeliverableAccessRule}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverableService {

    private final DeliverableRepository deliverableRepository;
    private final DeliverableTypeRepository typeRepository;
    private final FileService fileService;
    private final ProjectPolicy policy;
    private final ApplicationEventPublisher events;

    /** Los entregables de la cátedra con lo que el proyecto lleva subido. */
    @Transactional(readOnly = true)
    public List<DeliverableGroupResponse> list(Integer projectId, String email) {
        User viewer = policy.account(email);
        Project project = policy.findVisible(projectId, viewer);
        if (!policy.canSeeDeliverables(project, viewer)) {
            throw new ForbiddenActionException("Acepta la invitación para ver los entregables del equipo");
        }
        Map<Integer, List<Deliverable>> byType =
                deliverableRepository.findByProjectIdOrderByUploadedAtAsc(projectId).stream()
                        .collect(Collectors.groupingBy(
                                deliverable -> deliverable.getType().getId(), LinkedHashMap::new, Collectors.toList()));

        // Los generales de la cátedra más los del tipo de prototipo del proyecto.
        return typeRepository
                .findByEditionIdAndTrackOrderBySortOrderAscIdAsc(
                        project.getEdition().getId(), project.getTrack())
                .stream()
                .filter(type -> type.appliesTo(project))
                .map(type -> DeliverableGroupResponse.of(type, byType.getOrDefault(type.getId(), List.of())))
                .toList();
    }

    /**
     * Sube un archivo para un entregable.
     *
     * @throws ConflictException      si el plazo cerró o el entregable ya llegó a su tope de archivos
     * @throws InvalidFieldsException si el archivo no cumple el formato o el tamaño, o el entregable es un enlace
     */
    @Transactional
    public List<DeliverableGroupResponse> upload(Integer projectId, Integer typeId, MultipartFile file, String email) {
        // Antes que nada: dos subidas a la vez contarían los mismos archivos ya entregados.
        policy.lock(projectId);
        User uploader = policy.account(email);
        Project project = policy.findVisible(projectId, uploader);
        DeliverableType type = requireOpenSlot(project, typeId, uploader);
        if (type.getKind().isLink()) {
            throw new InvalidFieldsException(
                    FileService.FIELD, "\"" + type.getName() + "\" se entrega como enlace, no como archivo");
        }

        Deliverable deliverable = newDeliverable(project, type, uploader);
        deliverable.setFile(fileService.store(file, type.getKind().formats(), FileVisibility.PRIVATE, uploader));
        deliverableRepository.save(deliverable);
        log.info("Proyecto {}: archivo subido para el entregable {}", projectId, type.getName());

        return list(projectId, email);
    }

    /**
     * Registra un enlace para un entregable de tipo LINK.
     *
     * @throws ConflictException      si el plazo cerró o el entregable ya llegó a su tope
     * @throws InvalidFieldsException si la dirección no es http(s) o el entregable es un archivo
     */
    @Transactional
    public List<DeliverableGroupResponse> submitLink(Integer projectId, DeliverableLinkRequest request, String email) {
        policy.lock(projectId);
        User author = policy.account(email);
        Project project = policy.findVisible(projectId, author);
        DeliverableType type = requireOpenSlot(project, request.deliverableTypeId(), author);
        if (!type.getKind().isLink()) {
            throw new InvalidFieldsException(
                    "url", "\"" + type.getName() + "\" se entrega como archivo, no como enlace");
        }

        Deliverable deliverable = newDeliverable(project, type, author);
        deliverable.setUrl(validUrl(request.url()));
        deliverableRepository.save(deliverable);
        log.info("Proyecto {}: enlace registrado para el entregable {}", projectId, type.getName());

        return list(projectId, email);
    }

    /**
     * El entregable de la cátedra del proyecto, si esa cuenta está en el equipo,
     * el plazo (el propio del entregable o el de la edición) sigue abierto y aún
     * cabe uno más.
     */
    private DeliverableType requireOpenSlot(Project project, Integer typeId, User member) {
        policy.requireTeamMember(project, member);
        DeliverableType type = typeRepository
                .findById(typeId)
                .filter(candidate -> candidate
                                .getEdition()
                                .getId()
                                .equals(project.getEdition().getId())
                        && candidate.getTrack() == project.getTrack()
                        && candidate.appliesTo(project))
                .orElseThrow(() -> new NoSuchElementException("No existe un entregable con ID: " + typeId));
        policy.requireSubmissionOpen(project.getEdition(), type.getClosesOn());

        int uploaded = deliverableRepository.countByProjectIdAndTypeId(project.getId(), typeId);
        if (uploaded >= type.getMaxFiles()) {
            String what = type.getKind().isLink() ? "enlace" : "archivo";
            throw new ConflictException(
                    type.getMaxFiles() == 1
                            ? "\"" + type.getName() + "\" admite un solo " + what
                                    + ". Quita el que subiste para reemplazarlo"
                            : "\"" + type.getName() + "\" admite hasta " + type.getMaxFiles() + " " + what + "s");
        }
        return type;
    }

    private static Deliverable newDeliverable(Project project, DeliverableType type, User author) {
        Deliverable deliverable = new Deliverable();
        deliverable.setProject(project);
        deliverable.setType(type);
        deliverable.setUploadedBy(author);
        return deliverable;
    }

    /** La anotación ya exige http(s); aquí se comprueba que además sea una URL bien formada. */
    private static String validUrl(String url) {
        String trimmed = url.strip();
        try {
            URI uri = new URI(trimmed);
            if (uri.getHost() == null || !Set.of("http", "https").contains(uri.getScheme())) {
                throw new URISyntaxException(trimmed, "sin host o sin esquema");
            }
        } catch (URISyntaxException e) {
            throw new InvalidFieldsException("url", "Ingresa una dirección válida, como https://youtu.be/...");
        }
        return trimmed;
    }

    /** Quita un archivo o un enlace. Lo puede hacer cualquiera del equipo, no solo quien lo subió. */
    @Transactional
    public List<DeliverableGroupResponse> delete(Integer projectId, Integer deliverableId, String email) {
        User actor = policy.account(email);
        Project project = policy.findVisible(projectId, actor);
        policy.requireTeamMember(project, actor);

        Deliverable deliverable = deliverableRepository
                .findWithProjectById(deliverableId)
                .filter(candidate -> candidate.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NoSuchElementException("No existe un archivo con ID: " + deliverableId));
        policy.requireSubmissionOpen(project.getEdition(), deliverable.getType().getClosesOn());

        events.publishEvent(new AuditableAction(
                Action.DELIVERABLE_DELETED,
                deliverable.getId(),
                project.getTitle(),
                deliverable.getType().getName() + ": "
                        + (deliverable.isLink()
                                ? deliverable.getUrl()
                                : deliverable.getFile().getOriginalName())));
        deliverableRepository.delete(deliverable);
        if (!deliverable.isLink()) {
            fileService.delete(deliverable.getFile());
        }
        return list(projectId, email);
    }

    /**
     * Al eliminarse una inscripción se van sus archivos: la base los borraría en
     * cascada, pero el contenido en disco quedaría huérfano.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onProjectDeleted(ProjectDeletedEvent event) {
        List<Deliverable> deliverables = deliverableRepository.findByProjectIdOrderByUploadedAtAsc(event.projectId());
        deliverableRepository.deleteAll(deliverables);
        deliverableRepository.flush();
        deliverables.stream()
                .filter(deliverable -> !deliverable.isLink())
                .forEach(deliverable -> fileService.delete(deliverable.getFile()));
    }
}
