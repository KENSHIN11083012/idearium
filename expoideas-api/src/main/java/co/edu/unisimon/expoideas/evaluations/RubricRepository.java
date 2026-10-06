package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.editions.Track;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricRepository extends JpaRepository<Rubric, Integer> {

    /** La versión vigente de esa cátedra: la más reciente. */
    Optional<Rubric> findFirstByTrackOrderByVersionDesc(Track track);
}
