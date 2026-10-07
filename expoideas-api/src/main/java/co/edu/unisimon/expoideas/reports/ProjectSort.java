package co.edu.unisimon.expoideas.reports;

import org.springframework.data.domain.Sort;

/**
 * En qué orden sale el listado de proyectos, en pantalla y en el CSV. El id
 * desempata siempre: sin él, dos proyectos iguales en lo que se ordena podrían
 * cambiar de página entre una consulta y la siguiente.
 */
public enum ProjectSort {
    /** De la inscripción más reciente a la más antigua. */
    RECENT(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    /** Por título, de la A a la Z. */
    TITLE(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));

    private final Sort sort;

    ProjectSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
