import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { API_BASE_URL, get } from '@/lib/apiClient';
import { toQuery } from '@/lib/query';
import { session } from '@/lib/session';

/**
 * Una página del listado de proyectos para la gestión y los profesores. Los
 * filtros vacíos no se envían: la API los trata como «sin filtrar».
 *
 * @param {object} filters                 editionId, track, teacherId, sectorId, result y search
 * @param {{ page?: number, size?: number }} [paging] la página (desde 0) y cuántos por página
 */
export const useProjectDirectory = (filters, paging = {}) => {
    const search = toQuery({ ...filters, ...paging });
    return useQuery({
        queryKey: ['project-directory', search],
        queryFn: () => get(`/projects${search}`),
        // Al cambiar de página o de filtro se sigue viendo la anterior hasta que llega la nueva.
        placeholderData: keepPreviousData,
    });
};

/** Lo más que la API entrega en una página (PageResponse.MAX_SIZE). */
const LARGEST_PAGE = 200;

/**
 * Todos los proyectos que cumplen los filtros, página tras página. Es para lo
 * que no se puede mirar por partes, como la agenda de sustentaciones, que
 * ordena por la fecha de la cita y no por el orden del listado.
 */
export const useWholeProjectDirectory = (filters) => {
    const search = toQuery(filters);
    return useQuery({
        queryKey: ['project-directory', 'whole', search],
        queryFn: async () => {
            const projects = [];
            for (let page = 0, totalPages = 1; page < totalPages; page++) {
                const chunk = await get(`/projects${toQuery({ ...filters, page, size: LARGEST_PAGE })}`);
                projects.push(...chunk.items);
                totalPages = chunk.totalPages;
            }
            return projects;
        },
    });
};

/**
 * Descarga el listado en CSV con los filtros puestos. Va con fetch porque un
 * enlace no llevaría el token de la sesión.
 */
export const downloadProjectsCsv = async (filters) => {
    const response = await fetch(`${API_BASE_URL}/projects/export${toQuery(filters)}`, {
        headers: { Authorization: `Bearer ${session.token()}` },
    });
    if (!response.ok) {
        throw new Error('No pudimos generar el archivo.');
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = `proyectos-${new Date().toLocaleDateString('en-CA')}.csv`;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
};
