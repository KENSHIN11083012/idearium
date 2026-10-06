package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprobaciones de cátedra por persona, para la gestión. Sin las manuales, la
 * primera edición no tendría a nadie habilitado para INNPRENDE II.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackApprovalService {

    private final TrackApprovalRepository approvalRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TrackApprovalResponse> list(Integer userId) {
        return approvalRepository.findByUserIdOrderByTrackAsc(userId).stream()
                .map(TrackApprovalResponse::from)
                .toList();
    }

    /**
     * Aprobación manual, sin proyecto.
     *
     * @throws ConflictException      si esa persona ya tiene aprobada esa cátedra
     * @throws NoSuchElementException si la cuenta no existe
     */
    @Transactional
    public TrackApprovalResponse create(String actorEmail, TrackApprovalRequest request) {
        User actor = account(actorEmail);
        User user = userRepository
                .findById(request.userId())
                .orElseThrow(() -> new NoSuchElementException("No existe un usuario con ID: " + request.userId()));
        if (approvalRepository.existsByUserIdAndTrack(user.getId(), request.track())) {
            throw new ConflictException(user.fullName() + " ya tiene aprobada " + label(request.track()));
        }
        TrackApproval approval = approvalRepository.save(TrackApproval.of(user, request.track(), null, actor));
        log.info(
                "Usuario ID {}: {} aprobada a mano por el usuario ID {}", user.getId(), request.track(), actor.getId());
        return TrackApprovalResponse.from(approval);
    }

    /** Quita una aprobación, venga de un proyecto o de la gestión. */
    @Transactional
    public void delete(Integer id) {
        TrackApproval approval = approvalRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe una aprobación con ID: " + id));
        approvalRepository.delete(approval);
        log.info("Aprobación ID {} eliminada", id);
    }

    private User account(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No existe una cuenta con el correo: " + email));
    }

    static String label(Enum<?> track) {
        return track.name().replace('_', ' ');
    }
}
