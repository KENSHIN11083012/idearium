package co.edu.unisimon.expoideas.presentations;

import co.edu.unisimon.expoideas.editions.Track;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresentationRepository extends JpaRepository<Presentation, Integer> {

    Optional<Presentation> findByProjectId(Integer projectId);

    /** La agenda de una cátedra en una edición, de la primera cita a la última. */
    @EntityGraph(attributePaths = {"project", "project.teacher", "project.members", "project.members.user"})
    @Query("""
            select p from Presentation p
            where p.project.edition.id = :editionId and p.project.track = :track
            order by p.startsAt asc
            """)
    List<Presentation> agenda(@Param("editionId") Integer editionId, @Param("track") Track track);
}
