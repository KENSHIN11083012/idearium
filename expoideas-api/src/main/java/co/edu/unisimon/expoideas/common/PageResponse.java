package co.edu.unisimon.expoideas.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Una página de un listado. Los listados que crecen con el uso (cuentas,
 * proyectos, rastro de auditoría) no devuelven todo de una vez: devuelven una
 * página y cuántas hay.
 *
 * @param page       la página devuelta; la primera es 0
 * @param size       filas por página
 * @param totalItems cuántas filas cumplen los filtros, en todas las páginas
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static final int DEFAULT_SIZE = 25;

    /** Tope de filas por página: nadie pide un listado entero de una vez. */
    public static final int MAX_SIZE = 200;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return of(page.getContent().stream().map(mapper).toList(), page);
    }

    /** Con los elementos ya convertidos (o cargados aparte) y los números de la página de la base. */
    public static <T> PageResponse<T> of(List<T> items, Page<?> page) {
        return new PageResponse<>(
                items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * La petición a la base para esa página, con el tamaño y la página dentro de sus límites. La
     * página se acota para que la posición de su primera fila quepa en un entero, que es lo que
     * recibe la base: una página absurda devuelve una lista vacía, no un error.
     */
    public static Pageable request(int page, int size, Sort sort) {
        int rows = Math.clamp(size, 1, MAX_SIZE);
        return PageRequest.of(Math.clamp(page, 0, Integer.MAX_VALUE / rows - 1), rows, sort);
    }
}
