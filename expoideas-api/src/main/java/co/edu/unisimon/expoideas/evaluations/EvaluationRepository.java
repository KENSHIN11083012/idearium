package co.edu.unisimon.expoideas.evaluations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, Integer> {

    Optional<Evaluation> findByProjectIdAndJurorId(Integer projectId, Integer jurorId);

    /** Las evaluaciones de un proyecto, en el orden en que se hicieron. */
    @EntityGraph(attributePaths = {"juror"})
    List<Evaluation> findByProjectIdOrderByCreatedAtAsc(Integer projectId);

    /** Las evaluaciones de varios proyectos de una vez. */
    @EntityGraph(attributePaths = {"juror"})
    List<Evaluation> findByProjectIdInOrderByCreatedAtAsc(Collection<Integer> projectIds);

    /** Lo que esa persona ya calificó, lo más reciente primero. */
    List<Evaluation> findByJurorIdOrderByUpdatedAtDesc(Integer jurorId);

    boolean existsByJurorId(Integer jurorId);
}
