package co.edu.unisimon.expoideas.editions;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edición y consulta de las ediciones de la Expo. Las escribe MacondoLab;
 * leerlas es público, porque la portada anuncia cuándo abren las inscripciones.
 *
 * <p>Las reglas que cruzan campos se verifican aquí y salen como 400 con el
 * mensaje puesto en el campo que lo causó. Un nombre repetido choca con la
 * restricción única de la BD y GlobalExceptionHandler lo responde con 409.
 */
@Service
@RequiredArgsConstructor
public class EditionService {

    private final EditionRepository editionRepository;
    private final UserRepository userRepository;
    private final RubricCatalog rubricCatalog;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<EditionResponse> list() {
        LocalDate today = today();
        return editionRepository.findAllByOrderByRegistrationOpensOnDesc().stream()
                .map(edition -> EditionResponse.from(edition, today))
                .toList();
    }

    /** @throws NoSuchElementException si el id no existe */
    @Transactional(readOnly = true)
    public EditionResponse get(Integer id) {
        return EditionResponse.from(find(id), today());
    }

    @Transactional
    public EditionResponse create(EditionRequest request) {
        return save(new Edition(), request);
    }

    /** @throws NoSuchElementException si el id no existe */
    @Transactional
    public EditionResponse update(Integer id, EditionRequest request) {
        return save(find(id), request);
    }

    /**
     * Desde ahora los equipos de esa cátedra ven su nota y las observaciones.
     * Volver a publicar solo actualiza la fecha.
     *
     * @throws NoSuchElementException si la edición no existe o no tiene esa cátedra
     */
    @Transactional
    public EditionResponse publishGrades(Integer id, Track track, String actorEmail) {
        Edition edition = find(id);
        User actor = userRepository
                .findByEmail(actorEmail)
                .orElseThrow(() -> new NoSuchElementException("No existe una cuenta con el correo: " + actorEmail));
        trackOf(edition, track).publishGrades(actor, LocalDateTime.now(clock));
        events.publishEvent(
                new AuditableAction(Action.GRADES_PUBLISHED, edition.getId(), edition.getName(), track.label()));
        return EditionResponse.from(edition, today());
    }

    /** Los equipos dejan de ver su nota hasta que se vuelva a publicar. */
    @Transactional
    public EditionResponse hideGrades(Integer id, Track track) {
        Edition edition = find(id);
        trackOf(edition, track).hideGrades();
        events.publishEvent(
                new AuditableAction(Action.GRADES_HIDDEN, edition.getId(), edition.getName(), track.label()));
        return EditionResponse.from(edition, today());
    }

    private static EditionTrack trackOf(Edition edition, Track track) {
        return edition.track(track)
                .orElseThrow(() -> new NoSuchElementException("La edición no tiene configurada la cátedra " + track));
    }

    private Edition find(Integer id) {
        return editionRepository
                .findWithTracksById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe una edición con ID: " + id));
    }

    private EditionResponse save(Edition edition, EditionRequest request) {
        validateDates(request);
        Map<Track, TrackSettingsRequest> tracks = validateTracks(request);
        validateNoOverlap(edition, request);

        edition.setName(request.name().strip());
        edition.setRegistrationOpensOn(request.registrationOpensOn());
        edition.setRegistrationClosesOn(request.registrationClosesOn());
        edition.setSubmissionClosesOn(request.submissionClosesOn());
        tracks.forEach(
                (track, settings) -> edition.setTrackSettings(track, settings.minMembers(), settings.maxMembers()));
        // La rúbrica se fija una vez: una edición que ya tiene la suya no pasa sola a una versión nueva.
        edition.getTracks().stream()
                .filter(settings -> settings.getRubricId() == null)
                .forEach(settings -> settings.setRubricId(
                        rubricCatalog.currentRubricId(settings.getTrack()).orElse(null)));

        // saveAndFlush: un nombre repetido falla aquí, como DataIntegrityViolationException (409).
        return EditionResponse.from(editionRepository.saveAndFlush(edition), today());
    }

    private static void validateDates(EditionRequest request) {
        if (request.registrationClosesOn().isBefore(request.registrationOpensOn())) {
            throw new InvalidFieldsException(
                    "registrationClosesOn", "Las inscripciones no pueden cerrar antes de abrir");
        }
        if (request.submissionClosesOn().isBefore(request.registrationClosesOn())) {
            throw new InvalidFieldsException(
                    "submissionClosesOn", "Las entregas no pueden cerrar antes que las inscripciones");
        }
    }

    /** Las dos cátedras, cada una una sola vez y con el mínimo por debajo del máximo. */
    private static Map<Track, TrackSettingsRequest> validateTracks(EditionRequest request) {
        Map<Track, TrackSettingsRequest> tracks = new EnumMap<>(Track.class);
        for (TrackSettingsRequest settings : request.tracks()) {
            if (settings.track() == null) {
                continue; // Lo rechaza @NotNull antes de llegar aquí.
            }
            if (tracks.put(settings.track(), settings) != null) {
                throw new InvalidFieldsException("tracks", "Cada cátedra se configura una sola vez");
            }
            if (settings.minMembers() != null
                    && settings.maxMembers() != null
                    && settings.maxMembers() < settings.minMembers()) {
                throw new InvalidFieldsException("tracks", "El máximo de integrantes no puede ser menor que el mínimo");
            }
        }
        if (!tracks.keySet().containsAll(List.of(Track.values()))) {
            throw new InvalidFieldsException("tracks", "Configura las dos cátedras: INNPRENDE I e INNPRENDE II");
        }
        return tracks;
    }

    /**
     * Dos ediciones no pueden estar vivas a la vez: si se cruzaran, un proyecto
     * no sabría a cuál se inscribe.
     */
    private void validateNoOverlap(Edition edition, EditionRequest request) {
        Edition candidate = new Edition();
        candidate.setRegistrationOpensOn(request.registrationOpensOn());
        candidate.setSubmissionClosesOn(request.submissionClosesOn());

        editionRepository.findAllByOrderByRegistrationOpensOnDesc().stream()
                .filter(other -> !Objects.equals(other.getId(), edition.getId()))
                .filter(candidate::overlaps)
                .findFirst()
                .ifPresent(other -> {
                    throw new InvalidFieldsException(
                            "registrationOpensOn", "Estas fechas se cruzan con la edición " + other.getName());
                });
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
