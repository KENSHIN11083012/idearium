package co.edu.unisimon.expoideas.editions;

import java.util.Optional;

/**
 * Lo único que una edición necesita saber de las rúbricas: cuál estrena cada
 * cátedra. Lo implementa el módulo de evaluaciones; aquí no se sabe qué es una
 * rúbrica, solo se guarda su id.
 */
public interface RubricCatalog {

    /** La versión vigente de la rúbrica de esa cátedra, o vacío si todavía no tiene ninguna. */
    Optional<Integer> currentRubricId(Track track);
}
