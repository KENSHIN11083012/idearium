package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.catalogs.CatalogLookup;
import co.edu.unisimon.expoideas.catalogs.PrototypeType;
import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.editions.Edition;
import co.edu.unisimon.expoideas.editions.EditionRepository;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscripción de proyectos: alta, edición y consulta. El equipo lo maneja
 * {@link ProjectTeamService}.
 *
 * <p>Inscribe un estudiante, que queda como líder del proyecto, y solo mientras
 * la inscripción de esa edición está abierta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EditionRepository editionRepository;
    private final UserRepository userRepository;
    private final CatalogLookup catalogLookup;
    private final ProjectPolicy policy;
    private final TrackApprovalRepository approvalRepository;
    private final ApplicationEventPublisher events;

    /** Los proyectos de esa cuenta, incluidos aquellos a los que la invitaron. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine(String email) {
        User user = policy.account(email);
        LocalDate today = policy.today();
        return projectRepository.findAllOfMember(user.getId()).stream()
                .map(project -> ProjectResponse.from(project, today))
                .toList();
    }

    /**
     * Un proyecto para su equipo, su docente o la gestión.
     *
     * @throws NoSuchElementException si no existe o quien pregunta no puede verlo
     */
    @Transactional(readOnly = true)
    public ProjectResponse get(Integer id, String email) {
        User viewer = policy.account(email);
        return ProjectResponse.from(policy.findVisible(id, viewer), policy.today());
    }

    @Transactional
    public ProjectResponse create(String email, ProjectRequest request) {
        User leader = policy.account(email);
        policy.requireStudent(leader);

        Edition edition = editionRepository
                .findWithTracksById(request.editionId())
                .orElseThrow(() -> new NoSuchElementException("No existe una edición con ID: " + request.editionId()));
        if (edition.track(request.track()).isEmpty()) {
            throw new InvalidFieldsException("track", "Esa cátedra no está configurada en la edición");
        }
        policy.requireRegistrationOpen(edition);
        policy.requireNotOnAnotherTeam(
                edition, request.track(), leader, "Ya tienes un proyecto inscrito en esta cátedra");
        policy.requireEligibleFor(leader, edition, request.track());

        Project project = new Project();
        project.setEdition(edition);
        project.setTrack(request.track());
        apply(project, request);
        project.addMember(leader, MemberRole.LEADER, MembershipStatus.ACCEPTED, policy.now());

        try {
            // Se escribe ya, para que un rechazo de la base (un doble clic) salga aquí
            // y no al cerrar la transacción.
            return ProjectResponse.from(projectRepository.saveAndFlush(project), policy.today());
        } catch (DataIntegrityViolationException failure) {
            throw policy.onTeamSave(failure);
        }
    }

    /** Solo el líder, y solo mientras la inscripción sigue abierta. La cátedra no cambia. */
    /**
     * El resultado del proyecto, por su profesor o la gestión, después del cierre
     * de entregas. Aprobarlo deja a cada integrante aceptado con la cátedra
     * aprobada; volverlo a no aprobado retira esas aprobaciones (no las manuales
     * ni las de otro proyecto).
     *
     * @throws ForbiddenActionException si no es el profesor del grupo ni de la gestión
     * @throws ConflictException        si las entregas siguen abiertas
     */
    @Transactional
    public ProjectResponse setResult(Integer id, String email, ProjectResultRequest request) {
        User actor = policy.account(email);
        Project project = policy.findVisible(id, actor);
        policy.requireTeacherOrManagement(project, actor);
        policy.requireSubmissionClosed(project.getEdition());

        project.setResult(request.result(), actor, policy.now());
        if (request.result() == ProjectResult.APPROVED) {
            for (User member : project.acceptedMembers()) {
                if (!approvalRepository.existsByUserIdAndTrack(member.getId(), project.getTrack())) {
                    approvalRepository.save(TrackApproval.of(member, project.getTrack(), project, actor));
                }
            }
        } else {
            approvalRepository.deleteAll(approvalRepository.findByProjectId(project.getId()));
        }
        log.info("Proyecto {}: resultado {} por el usuario ID {}", id, request.result(), actor.getId());
        return ProjectResponse.from(projectRepository.save(project), policy.today());
    }

    @Transactional
    public ProjectResponse update(Integer id, String email, ProjectRequest request) {
        User actor = policy.account(email);
        Project project = policy.findVisible(id, actor);
        policy.requireLeader(project, actor);
        policy.requireRegistrationOpen(project.getEdition());

        apply(project, request);
        return ProjectResponse.from(projectRepository.save(project), policy.today());
    }

    /**
     * Borra la inscripción con su equipo y sus entregables. Solo el líder y solo
     * con la inscripción abierta.
     */
    @Transactional
    public void delete(Integer id, String email) {
        User actor = policy.account(email);
        Project project = policy.findVisible(id, actor);
        policy.requireLeader(project, actor);
        policy.requireRegistrationOpen(project.getEdition());

        events.publishEvent(new AuditableAction(
                Action.PROJECT_DELETED,
                project.getId(),
                project.getTitle(),
                project.getEdition().getName() + " · " + project.getTrack().label() + " · integrantes: "
                        + project.getMembers().size()));
        // Los módulos que cuelgan del proyecto se llevan lo suyo antes del borrado.
        events.publishEvent(new ProjectDeletedEvent(project.getId()));
        projectRepository.delete(project);
    }

    /** Lo que sí se puede cambiar después de inscribir. */
    private void apply(Project project, ProjectRequest request) {
        project.setTitle(request.title().strip());
        project.setSummary(request.summary().strip());
        project.setSector(catalogLookup.sector(request.sectorId()));
        project.setPrototypeType(prototypeType(project.getTrack(), request.prototypeTypeId()));
        project.setTeacher(teacher(request.teacherId()));
    }

    /**
     * El tipo de prototipo es cosa de INNPRENDE II. Se exige solo cuando
     * MacondoLab ya cargó el catálogo: antes, los proyectos de II van sin tipo.
     *
     * @throws InvalidFieldsException si viene en I, o falta en II con catálogo cargado
     */
    private PrototypeType prototypeType(Track track, Integer prototypeTypeId) {
        if (track != Track.INNPRENDE_II) {
            if (prototypeTypeId != null) {
                throw new InvalidFieldsException("prototypeTypeId", "El tipo de prototipo es solo para INNPRENDE II");
            }
            return null;
        }
        if (prototypeTypeId == null) {
            if (catalogLookup.hasPrototypeTypes()) {
                throw new InvalidFieldsException("prototypeTypeId", "Elige el tipo de prototipo");
            }
            return null;
        }
        return catalogLookup.prototypeType(prototypeTypeId);
    }

    private User teacher(Integer id) {
        return userRepository
                .findById(id)
                .filter(candidate -> candidate.getRole() == Role.TEACHER)
                .orElseThrow(() -> new InvalidFieldsException("teacherId", "Selecciona un profesor de la lista"));
    }
}
