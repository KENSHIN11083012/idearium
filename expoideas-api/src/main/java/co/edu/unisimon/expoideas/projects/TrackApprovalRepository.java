package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackApprovalRepository extends JpaRepository<TrackApproval, Integer> {

    /** Si esa persona aprobó esa cátedra, por proyecto o a mano. */
    boolean existsByUserIdAndTrack(Integer userId, Track track);

    Optional<TrackApproval> findByUserIdAndTrack(Integer userId, Track track);

    /** Las aprobaciones de una persona, con lo que la gestión necesita mostrar. */
    @EntityGraph(attributePaths = {"user", "project", "approvedBy"})
    List<TrackApproval> findByUserIdOrderByTrackAsc(Integer userId);

    /** Las que nacieron de un proyecto: se van si deja de estar aprobado. */
    List<TrackApproval> findByProjectId(Integer projectId);
}
