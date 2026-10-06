package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.editions.RubricCatalog;
import co.edu.unisimon.expoideas.editions.Track;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Le dice a una edición nueva con qué rúbrica nace cada cátedra: la versión más reciente. */
@Component
@RequiredArgsConstructor
class CurrentRubrics implements RubricCatalog {

    private final RubricRepository rubricRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> currentRubricId(Track track) {
        return rubricRepository.findFirstByTrackOrderByVersionDesc(track).map(Rubric::getId);
    }
}
