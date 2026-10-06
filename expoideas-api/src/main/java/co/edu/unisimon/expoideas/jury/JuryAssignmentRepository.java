package co.edu.unisimon.expoideas.jury;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JuryAssignmentRepository extends JpaRepository<JuryAssignment, Integer> {

    boolean existsByProjectIdAndUserId(Integer projectId, Integer userId);

    Optional<JuryAssignment> findByProjectIdAndUserId(Integer projectId, Integer userId);

    /** Los jurados de un proyecto, en el orden en que se asignaron. */
    @EntityGraph(attributePaths = {"user"})
    List<JuryAssignment> findByProjectIdOrderByCreatedAtAsc(Integer projectId);

    /** Los jurados de varios proyectos de una vez. */
    @EntityGraph(attributePaths = {"user"})
    List<JuryAssignment> findByProjectIdInOrderByCreatedAtAsc(Collection<Integer> projectIds);

    /** Las asignaciones de esa persona, con cada proyecto listo para mostrar su ficha. */
    @EntityGraph(
            attributePaths = {
                "project.edition",
                "project.sector",
                "project.teacher",
                "project.members",
                "project.members.user"
            })
    List<JuryAssignment> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
