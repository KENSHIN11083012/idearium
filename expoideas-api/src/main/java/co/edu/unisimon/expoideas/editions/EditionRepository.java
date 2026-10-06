package co.edu.unisimon.expoideas.editions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EditionRepository extends JpaRepository<Edition, Integer> {

    /** De la más reciente a la más antigua, con su configuración ya cargada. */
    @EntityGraph(attributePaths = "tracks")
    List<Edition> findAllByOrderByRegistrationOpensOnDesc();

    @EntityGraph(attributePaths = "tracks")
    Optional<Edition> findWithTracksById(Integer id);
}
