package co.edu.unisimon.expoideas.jury;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectPolicy;
import co.edu.unisimon.expoideas.projects.ProjectResponse;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jurados por proyecto. Los asigna la gestión (ver SecurityConfig) entre
 * profesores, jurados externos y cuentas de gestión; nunca el profesor del
 * grupo ni alguien del equipo. Cada persona consulta los proyectos que le
 * tocan; el acceso a la ficha y a los entregables lo da {@link JuryVisibilityRule}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JuryService {

    private static final String EMAIL = "email";

    private final JuryAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ProjectPolicy policy;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<JurorResponse> list(Integer projectId) {
        policy.find(projectId);
        return assignmentRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(JurorResponse::from)
                .toList();
    }

    /**
     * @throws InvalidFieldsException si el correo no tiene cuenta, el rol no puede ser jurado,
     *                                es el profesor del grupo o está en el equipo
     * @throws ConflictException      si ya es jurado de ese proyecto
     */
    @Transactional
    public JurorResponse assign(Integer projectId, String actorEmail, JurorRequest request) {
        User actor = policy.account(actorEmail);
        Project project = policy.find(projectId);
        User juror = userRepository
                .findByEmail(request.email().strip())
                .orElseThrow(() -> new InvalidFieldsException(EMAIL, "No hay una cuenta registrada con ese correo"));

        // Primero lo que tiene que ver con este proyecto; el rol, al final: es el motivo más genérico.
        if (project.getTeacher().getId().equals(juror.getId())) {
            throw new InvalidFieldsException(EMAIL, "El profesor del grupo no puede ser jurado de su propio proyecto");
        }
        if (project.memberOf(juror).isPresent()) {
            throw new InvalidFieldsException(EMAIL, "Esa persona está en el equipo del proyecto");
        }
        if (!canBeJuror(juror.getRole())) {
            throw new InvalidFieldsException(EMAIL, "Solo profesores, jurados o cuentas de gestión pueden ser jurados");
        }
        if (assignmentRepository.existsByProjectIdAndUserId(projectId, juror.getId())) {
            throw new ConflictException(juror.fullName() + " ya es jurado de este proyecto");
        }

        JuryAssignment saved = assignmentRepository.save(JuryAssignment.of(project, juror, actor));
        log.info(
                "Proyecto {}: usuario ID {} asignado como jurado por el usuario ID {}",
                projectId,
                juror.getId(),
                actor.getId());
        return JurorResponse.from(saved);
    }

    /** @throws NoSuchElementException si esa persona no es jurado del proyecto */
    @Transactional
    public void remove(Integer projectId, Integer userId) {
        JuryAssignment assignment = assignmentRepository
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NoSuchElementException("Esa persona no es jurado de este proyecto"));
        // Su evaluación se conserva pero deja de contar: la nota del proyecto puede cambiar.
        User juror = assignment.getUser();
        events.publishEvent(new AuditableAction(
                Action.JUROR_REMOVED,
                projectId,
                assignment.getProject().getTitle(),
                "Jurado: " + juror.fullName() + " (" + juror.getEmail() + ")"));
        assignmentRepository.delete(assignment);
        log.info("Proyecto {}: usuario ID {} deja de ser jurado", projectId, userId);
    }

    /** Los proyectos que quien consulta tiene por evaluar. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> myProjects(String email) {
        User juror = policy.account(email);
        return assignmentRepository.findByUserIdOrderByCreatedAtDesc(juror.getId()).stream()
                .map(JuryAssignment::getProject)
                .map(project -> ProjectResponse.from(project, policy.today()))
                .toList();
    }

    static boolean canBeJuror(Role role) {
        return role == Role.TEACHER || role == Role.JUDGE || role.isManagement();
    }
}
