package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.AccountAnonymizedEvent;
import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.ForbiddenActionException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.common.PageResponse;
import co.edu.unisimon.expoideas.common.ValidationPatterns;
import co.edu.unisimon.expoideas.files.FileService;
import co.edu.unisimon.expoideas.files.StoredFile;
import co.edu.unisimon.expoideas.notifications.AccountCreatedEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de cuentas por MacondoLab y administradores: listar, crear, cambiar
 * rol o adscripción, restablecer contraseñas, suspender, anonimizar y eliminar. SecurityConfig decide
 * quién entra a estas rutas; aquí se decide a quién se puede gestionar (con
 * {@link Role#canManage}), con la cuenta de la sesión como actor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RosterRepository rosterRepository;
    private final AccountTokenRepository tokenRepository;
    private final AffiliationResolver affiliation;
    private final PasswordUpdater passwords;
    private final FileService fileService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Motivos para no eliminar una cuenta que aportan otros módulos. Puede estar vacía. */
    private final List<AccountDeletionRule> deletionRules;

    /** Las más recientes primero: una cuenta nueva suele ser la que hay que atender. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("id"));

    /**
     * @param search      parte del nombre o del correo; vacío, todas
     * @param role        solo ese rol; null, todos
     * @param pendingOnly solo las que esperan que se confirme su rol del listado de la cátedra
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String search, Role role, boolean pendingOnly, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.strip();
        return PageResponse.from(
                userRepository.search(term, role, pendingOnly, PageResponse.request(page, size, NEWEST_FIRST)),
                UserResponse::from);
    }

    /**
     * Alta con contraseña temporal: jurados externos o cuentas que nacen con un
     * rol distinto de estudiante. La autorización de datos queda pendiente porque
     * no la dio la persona; la da en su primer ingreso.
     *
     * @throws ForbiddenActionException si el actor no puede gestionar el rol pedido
     * @throws InvalidFieldsException   si el correo no es institucional (salvo jurados) o falta la adscripción
     * @throws ConflictException        si el correo ya está en uso
     * @throws NoSuchElementException   si el campus, la facultad o el programa no existen
     */
    @Transactional
    public UserResponse create(String actorEmail, UserCreateRequest request) {
        User actor = findByEmail(actorEmail);
        if (!actor.getRole().canManage(request.role())) {
            throw new ForbiddenActionException(
                    "Solo un administrador puede crear cuentas de administración o de MacondoLab.");
        }
        String email = request.email().strip();
        Map<String, String> invalid = new LinkedHashMap<>();
        if (request.role() != Role.JUDGE && !email.matches(ValidationPatterns.INSTITUTIONAL_EMAIL)) {
            invalid.put("email", "Solo los jurados pueden tener un correo externo; usa uno @unisimon.edu.co");
        }
        if (request.role().requiresAffiliation()) {
            invalid.putAll(AffiliationResolver.missingFields(request.campusId(), request.facultyId()));
        }
        if (!invalid.isEmpty()) {
            throw new InvalidFieldsException(invalid);
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("El correo " + email + " ya pertenece a otro usuario.");
        }

        User user = User.builder()
                .firstName(request.firstName().strip())
                .lastName(request.lastName().strip())
                .email(email)
                .passwordHash(passwords.hash(request.password()))
                .role(request.role())
                .mustChangePassword(true)
                .build();
        if (request.role().requiresAffiliation()) {
            affiliation.assign(user, request.campusId(), request.facultyId(), request.academicProgramId());
        }

        User saved = userRepository.save(user);
        log.info("Usuario ID {} creado desde la gestión con rol {}", saved.getId(), saved.getRole());
        // La contraseña temporal le llega por correo: es como se entera de que ya puede entrar.
        events.publishEvent(new AccountCreatedEvent(
                saved.getEmail(),
                saved.fullName(),
                request.password(),
                saved.getRole().label()));
        return UserResponse.from(saved);
    }

    /**
     * Cambia rol y adscripción. Enviar un rol, aunque sea el que la cuenta ya
     * tiene, resuelve el que el listado de la cátedra hubiera dejado pendiente.
     *
     * @throws ForbiddenActionException si el actor no puede gestionar la cuenta o el rol pedido, o cambia su propio rol
     * @throws IllegalArgumentException si llega adscripción para un rol que no la lleva
     * @throws InvalidFieldsException   si el programa es de otra facultad
     * @throws NoSuchElementException   si la cuenta, el campus, la facultad o el programa no existen
     */
    @Transactional
    public UserResponse update(String actorEmail, Integer id, UserUpdateRequest request) {
        User actor = findByEmail(actorEmail);
        User user = findById(id);
        requireCanManage(actor, user);
        requireStillSomeones(user);
        Role previous = user.getRole();
        Role pending = user.getPendingRole();
        if (request.role() != null && request.role() != previous) {
            if (actor.getId().equals(user.getId())) {
                throw new ForbiddenActionException("No puedes cambiar tu propio rol.");
            }
            if (!actor.getRole().canManage(request.role())) {
                throw new ForbiddenActionException(
                        "Solo un administrador puede asignar los roles Administrador o MacondoLab.");
            }
            user.setRole(request.role());
            String change = previous.label() + " → " + request.role().label();
            audit(Action.ROLE_CHANGED, user, request.role() == pending ? change + " (lo pedía el listado)" : change);
        } else if (request.role() != null && pending != null) {
            audit(Action.PENDING_ROLE_DISCARDED, user, "Pedía " + pending.label() + "; sigue como " + previous.label());
        }
        if (request.role() != null) {
            // La gestión ya decidió el rol de esta cuenta, sea el del listado u otro: no queda nada por confirmar.
            user.setPendingRole(null);
        }

        boolean hasAffiliation =
                request.campusId() != null || request.facultyId() != null || request.academicProgramId() != null;
        if (hasAffiliation && !user.getRole().requiresAffiliation()) {
            throw new IllegalArgumentException("El rol de este usuario no lleva adscripción académica.");
        }
        if (request.campusId() != null) {
            affiliation.assignCampus(user, request.campusId());
        }
        if (request.facultyId() != null) {
            affiliation.assignFaculty(user, request.facultyId(), request.academicProgramId());
        } else if (request.academicProgramId() != null) {
            affiliation.assignProgram(user, request.academicProgramId());
        }

        log.info("Usuario ID {} actualizado desde la gestión", id);
        return UserResponse.from(user);
    }

    /**
     * Restablece la contraseña sin conocer la actual. Queda como temporal: se
     * cambia en el siguiente ingreso.
     *
     * @throws ForbiddenActionException si el actor no puede gestionar la cuenta o es la suya
     * @throws InvalidFieldsException   si la confirmación no coincide o es igual a la actual
     * @throws NoSuchElementException   si la cuenta no existe
     */
    @Transactional
    public void resetPassword(String actorEmail, Integer id, PasswordResetRequest request) {
        User actor = findByEmail(actorEmail);
        User user = findById(id);
        if (actor.getId().equals(user.getId())) {
            // Por aquí no se pide la contraseña actual: la propia se cambia en /users/me/password.
            throw new ForbiddenActionException(
                    "Para cambiar tu propia contraseña usa la opción Seguridad de tu cuenta.");
        }
        requireCanManage(actor, user);
        requireStillSomeones(user);
        passwords.replace(user, request.newPassword(), request.confirmPassword(), true);
        audit(Action.PASSWORD_RESET, user, null);
        log.info("Contraseña del usuario ID {} restablecida desde la gestión", id);
    }

    /**
     * Corta el acceso de una cuenta sin eliminarla: no inicia sesión y la que
     * tuviera abierta deja de valer. Es lo que queda cuando la cuenta tiene
     * historia (un equipo, un proyecto a cargo, una evaluación) y no se puede borrar.
     *
     * @throws ForbiddenActionException si el actor no puede gestionar la cuenta o es la suya
     * @throws NoSuchElementException   si la cuenta no existe
     */
    @Transactional
    public UserResponse suspend(String actorEmail, Integer id) {
        User actor = findByEmail(actorEmail);
        User user = findById(id);
        if (actor.getId().equals(user.getId())) {
            throw new ForbiddenActionException("No puedes suspender tu propia cuenta.");
        }
        requireCanManage(actor, user);
        requireStillSomeones(user);
        user.suspend();
        audit(Action.ACCOUNT_SUSPENDED, user, null);
        log.info("Usuario ID {} suspendido por el usuario ID {}", id, actor.getId());
        return UserResponse.from(user);
    }

    /**
     * Devuelve el acceso a una cuenta suspendida. Las sesiones que tenía no
     * reviven: vuelve a entrar con su contraseña.
     *
     * @throws ForbiddenActionException si el actor no puede gestionar la cuenta
     * @throws NoSuchElementException   si la cuenta no existe
     */
    @Transactional
    public UserResponse reactivate(String actorEmail, Integer id) {
        User actor = findByEmail(actorEmail);
        User user = findById(id);
        requireCanManage(actor, user);
        requireStillSomeones(user);
        user.reactivate();
        audit(Action.ACCOUNT_REACTIVATED, user, null);
        log.info("Usuario ID {} reactivado por el usuario ID {}", id, actor.getId());
        return UserResponse.from(user);
    }

    /**
     * Anonimiza la cuenta: le quita el nombre, el correo, la foto, los enlaces
     * pendientes y el acceso, y la saca del listado de la cátedra. Es lo que se
     * hace cuando alguien pide que se supriman sus datos y la cuenta tiene
     * historia (un equipo, un proyecto a cargo, una evaluación) que impide
     * eliminarla: el historial sigue, a nombre de «Cuenta anonimizada». Los
     * archivos que subió como entregables son del equipo y se quedan.
     *
     * <p>Solo administradores (lo exige SecurityConfig). No tiene vuelta atrás.
     *
     * @throws ForbiddenActionException si es la propia cuenta
     * @throws ConflictException        si ya estaba anonimizada
     * @throws NoSuchElementException   si el id no existe
     */
    @Transactional
    public UserResponse anonymize(String actorEmail, Integer id) {
        User actor = findByEmail(actorEmail);
        User user = findById(id);
        if (actor.getId().equals(user.getId())) {
            throw new ForbiddenActionException("No puedes anonimizar tu propia cuenta.");
        }
        requireStillSomeones(user);

        String formerEmail = user.getEmail();
        String formerName = user.nameForGreeting();
        String roleLabel = user.getRole().label();
        StoredFile photo = user.getPhoto();

        user.setPhoto(null);
        // Una contraseña que nadie conoce: aunque la cuenta se reactivara, no habría con qué entrar.
        user.anonymize(passwords.hash(UUID.randomUUID().toString()), LocalDateTime.now(clock));
        if (photo != null) {
            fileService.delete(photo);
        }
        tokenRepository.deleteByUserId(user.getId());
        rosterRepository.deleteByEmail(formerEmail.toLowerCase(Locale.ROOT));

        // El rastro guarda copias del correo y del nombre: se quitan en esta misma transacción.
        events.publishEvent(new AccountAnonymizedEvent(user.getId(), formerEmail, formerName, user.getEmail()));
        // En el rastro queda que se anonimizó, sin volver a escribir de quién era.
        audit(Action.ACCOUNT_ANONYMIZED, user, roleLabel);
        log.info("Usuario ID {} anonimizado por el usuario ID {}", id, actor.getId());
        return UserResponse.from(user);
    }

    /**
     * Elimina la cuenta con sus archivos. Solo administradores (lo exige SecurityConfig).
     *
     * @throws ForbiddenActionException si es la propia cuenta
     * @throws NoSuchElementException   si el id no existe
     */
    @Transactional
    public void delete(String actorEmail, Integer id) {
        User actor = findByEmail(actorEmail);
        User user = findById(id);
        if (actor.getId().equals(user.getId())) {
            throw new ForbiddenActionException("No puedes eliminar tu propia cuenta.");
        }
        // Otros módulos pueden tener motivos para conservarla (estar en un equipo,
        // ser el docente de un proyecto...). Se explica el primero que aparezca.
        deletionRules.stream()
                .map(rule -> rule.reasonToKeep(user))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(reason -> {
                    throw new ConflictException(reason);
                });
        // Antes de borrarla: después ya no hay de dónde sacar quién era.
        audit(
                Action.ACCOUNT_DELETED,
                user,
                user.fullName() + " · " + user.getRole().label());
        // Sus archivos no pueden quedar sin dueño: se borran con la cuenta.
        user.setPhoto(null);
        fileService.deleteAllOwnedBy(user);
        userRepository.delete(user);
        log.info("Usuario ID {} eliminado", id);
    }

    /** Deja en el rastro una acción sobre esa cuenta; quien la hizo es la cuenta de la sesión. */
    private void audit(Action action, User target, String detail) {
        events.publishEvent(new AuditableAction(action, target.getId(), target.getEmail(), detail));
    }

    /** Una cuenta anonimizada ya no es de nadie: no se le cambia nada ni se le devuelve el acceso. */
    private static void requireStillSomeones(User user) {
        if (user.isAnonymized()) {
            throw new ConflictException("Esta cuenta fue anonimizada: ya no se puede modificar.");
        }
    }

    private static void requireCanManage(User actor, User target) {
        if (!actor.getRole().canManage(target.getRole())) {
            throw new ForbiddenActionException(
                    "Solo un administrador puede modificar cuentas de administración o de MacondoLab.");
        }
    }

    private User findById(Integer id) {
        return userRepository
                .findWithProfileById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe un usuario con ID: " + id));
    }

    private User findByEmail(String email) {
        return userRepository
                .findWithProfileByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No existe un usuario con correo: " + email));
    }
}
