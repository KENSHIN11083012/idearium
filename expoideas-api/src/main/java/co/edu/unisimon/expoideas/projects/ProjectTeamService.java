package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.ForbiddenActionException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.notifications.TeamInvitationEvent;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El equipo de un proyecto: invitaciones, respuestas y salidas.
 *
 * <p>El líder invita a estudiantes ya registrados, buscándolos por su nombre o
 * con su correo, y cada quien acepta o rechaza desde su cuenta: nadie queda en
 * un equipo sin enterarse. Salir es
 * distinto: a un integrante lo retira el líder o la gestión, no él mismo. El
 * equipo solo se mueve mientras la inscripción está abierta.
 */
@Service
@RequiredArgsConstructor
public class ProjectTeamService {

    /** Campo al que se atribuyen los errores de la invitación, venga por correo o por cuenta. */
    private static final String EMAIL = "email";

    /** Con menos letras la búsqueda devolvería media cátedra, y no sirve para encontrar a nadie. */
    static final int MIN_SEARCH_LENGTH = 3;

    /** La búsqueda es para elegir a un compañero, no para recorrer el listado de estudiantes. */
    static final int MAX_CANDIDATES = 10;

    private static final Sort BY_NAME = Sort.by("firstName", "lastName", "id");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectPolicy policy;
    private final ApplicationEventPublisher events;

    /** Invita a un compañero. Devuelve el proyecto con el equipo ya actualizado. */
    @Transactional
    public ProjectResponse invite(Integer projectId, String email, InvitationRequest request) {
        // Antes que nada: dos invitaciones a la vez contarían el mismo cupo libre.
        policy.lock(projectId);
        User actor = policy.account(email);
        Project project = policy.findVisible(projectId, actor);
        policy.requireLeader(project, actor);
        policy.requireRegistrationOpen(project.getEdition());
        policy.requireFreeSeat(project);

        User invitee = invitee(request);
        if (invitee.getRole() != Role.STUDENT) {
            throw new InvalidFieldsException(EMAIL, "Solo puedes invitar a estudiantes");
        }
        if (project.memberOf(invitee).isPresent()) {
            throw new InvalidFieldsException(EMAIL, "Esa persona ya está en el equipo o tiene una invitación");
        }
        if (projectRepository.isAlreadyOnATeam(project.getEdition().getId(), project.getTrack(), invitee.getId())) {
            throw new InvalidFieldsException(EMAIL, "Esa persona ya tiene un proyecto en esta cátedra");
        }
        try {
            policy.requireEligibleFor(invitee, project.getEdition(), project.getTrack());
        } catch (ConflictException | ForbiddenActionException notEligible) {
            // Para el líder es un problema con el correo que escribió: se muestra en ese campo.
            throw new InvalidFieldsException(EMAIL, notEligible.getMessage());
        }

        project.addMember(invitee, MemberRole.MEMBER, MembershipStatus.INVITED, policy.now());
        // Quien invita es el líder: su celular es suyo.
        ProjectResponse response = ProjectResponse.from(projectRepository.save(project), policy.today(), true);
        // El correo sale después del commit; si la invitación no se guarda, no hay aviso.
        events.publishEvent(new TeamInvitationEvent(
                invitee.getEmail(),
                invitee.nameForGreeting(),
                actor.fullName(),
                project.getTitle(),
                project.getTrack().label()));
        return response;
    }

    /** A quién se invita: a la cuenta elegida en la búsqueda o, si no, a la de ese correo. */
    private User invitee(InvitationRequest request) {
        if (request.userId() != null) {
            return userRepository
                    .findById(request.userId())
                    .orElseThrow(() -> new InvalidFieldsException(EMAIL, "Esa cuenta ya no existe"));
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new InvalidFieldsException(EMAIL, "Indica a quién invitas: elige a tu compañero o escribe su correo");
        }
        return userRepository
                // La columna usa una collation que ignora mayúsculas: basta con recortar.
                .findByEmail(request.email().strip())
                .orElseThrow(() -> new InvalidFieldsException(EMAIL, "No hay una cuenta registrada con ese correo"));
    }

    /**
     * Estudiantes que el líder puede invitar y cuyo nombre contiene ese texto, sin
     * los que ya están en el equipo o tienen su invitación. Solo para el líder y
     * con la inscripción abierta, que es cuando se puede invitar; como mucho
     * {@value #MAX_CANDIDATES}, y nada con menos de {@value #MIN_SEARCH_LENGTH}
     * letras. Que alguien salga aquí no garantiza que se le pueda invitar (puede
     * tener ya otro equipo): eso lo dice la invitación.
     */
    @Transactional(readOnly = true)
    public List<InvitationCandidateResponse> candidates(Integer projectId, String email, String search) {
        User actor = policy.account(email);
        Project project = policy.findVisible(projectId, actor);
        policy.requireLeader(project, actor);
        policy.requireRegistrationOpen(project.getEdition());

        String name = search == null ? "" : search.strip();
        if (name.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        List<Integer> team = project.getMembers().stream()
                .map(member -> member.getUser().getId())
                .toList();
        return userRepository
                .findActiveByRoleAndName(Role.STUDENT, name, team, PageRequest.of(0, MAX_CANDIDATES, BY_NAME))
                .stream()
                .map(InvitationCandidateResponse::from)
                .toList();
    }

    /** Invitaciones sin responder de quien consulta. */
    @Transactional(readOnly = true)
    public List<InvitationResponse> listMine(String email) {
        User user = policy.account(email);
        return memberRepository
                .findByUserIdAndStatusOrderByInvitedAtDesc(user.getId(), MembershipStatus.INVITED)
                .stream()
                .map(InvitationResponse::from)
                .toList();
    }

    /**
     * Quien acepta dice el semestre que cursa. El lugar ya estaba reservado por la
     * invitación, así que no se revisa el cupo.
     */
    @Transactional
    public ProjectResponse accept(Integer invitationId, String email, InvitationAcceptanceRequest request) {
        ProjectMember invitation = invitationOf(invitationId, email);
        Project project = invitation.getProject();
        policy.requireRegistrationOpen(project.getEdition());
        policy.requireNotOnAnotherTeam(
                project.getEdition(),
                project.getTrack(),
                invitation.getUser(),
                "Ya tienes un proyecto inscrito en esta cátedra");
        policy.requireEligibleFor(invitation.getUser(), project.getEdition(), project.getTrack());

        invitation.accept(policy.now(), request.semester());
        try {
            // Se escribe ya, para que un rechazo de la base salga aquí y no al cerrar la transacción.
            memberRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException failure) {
            throw policy.onTeamSave(failure);
        }
        return ProjectResponse.from(project, policy.today());
    }

    /** Rechazar borra la invitación: el líder puede volver a invitar. */
    @Transactional
    public void decline(Integer invitationId, String email) {
        ProjectMember invitation = invitationOf(invitationId, email);
        invitation.getProject().removeMember(invitation);
        memberRepository.delete(invitation);
    }

    /**
     * Saca del equipo a un integrante. Lo hace el líder o la gestión, y queda en el
     * rastro quién fue; retirar una invitación sin responder no deja rastro, porque
     * esa persona nunca estuvo en el equipo. El líder no puede salir: el proyecto
     * quedaría sin responsable.
     */
    @Transactional
    public void remove(Integer projectId, Integer userId, String email) {
        User actor = policy.account(email);
        Project project = policy.findVisible(projectId, actor);
        policy.requireRegistrationOpen(project.getEdition());

        ProjectMember target = project.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Esa persona no está en el equipo"));

        if (target.isLeader()) {
            throw new ForbiddenActionException(
                    "El líder no puede salir del equipo. Si el grupo se deshizo, elimina la inscripción");
        }
        policy.requireCanRemove(project, actor, target);

        if (target.isAccepted()) {
            User member = target.getUser();
            events.publishEvent(new AuditableAction(
                    Action.TEAM_MEMBER_REMOVED,
                    project.getId(),
                    project.getTitle(),
                    "Integrante: " + member.fullName() + " (" + member.getEmail() + ")"));
        }
        project.removeMember(target);
        memberRepository.delete(target);
    }

    /** La invitación, si existe, está sin responder y es de quien la responde. */
    private ProjectMember invitationOf(Integer invitationId, String email) {
        User actor = policy.account(email);
        ProjectMember invitation = memberRepository
                .findWithProjectById(invitationId)
                .filter(member -> member.getUser().getId().equals(actor.getId()))
                .orElseThrow(() -> new NoSuchElementException("No existe una invitación con ID: " + invitationId));
        if (invitation.isAccepted()) {
            throw new ConflictException("Esa invitación ya fue aceptada");
        }
        return invitation;
    }
}
