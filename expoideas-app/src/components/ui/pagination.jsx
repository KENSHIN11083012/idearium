import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './button';

/**
 * Paso entre las páginas de un listado. La API devuelve una página y cuántas
 * hay (PageResponse); si todo cabe en una, no se pinta nada.
 *
 * @param {number} page       la página actual; la primera es 0
 * @param {number} totalPages
 * @param {number} totalItems cuántas filas hay en total, con los filtros puestos
 * @param {(page: number) => void} onChange
 * @param {string} [label]    nombre de la navegación, si la página tiene más de una
 */
export function Pagination({ page, totalPages, totalItems, onChange, label = 'Páginas' }) {
    if (!totalPages || totalPages <= 1) return null;

    return (
        <nav aria-label={label} className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-on-surface-variant" aria-live="polite">
                Página {page + 1} de {totalPages} · {totalItems} en total
            </p>
            <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => onChange(page - 1)} disabled={page <= 0}>
                    <ChevronLeft /> Anterior
                </Button>
                <Button variant="outline" size="sm" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1}>
                    Siguiente <ChevronRight />
                </Button>
            </div>
        </nav>
    );
}
