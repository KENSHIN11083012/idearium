import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { byName } from '@/lib/text';
import { CATALOG_PATHS, catalogApi } from './api';

/** Una sola clave por catálogo: lo que se guarda en Catálogos se ve en los formularios. */
const catalogKey = (path) => ['catalog', path];

const listQuery = (path) => ({ queryKey: catalogKey(path), queryFn: () => catalogApi.list(path) });

/** Registros de un catálogo. */
export const useCatalogItems = (path) => useQuery(listQuery(path));

/** Crea (sin id) o edita (con id) un registro y recarga el catálogo. */
export const useSaveCatalogItem = (path) => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, body }) => (id ? catalogApi.update(path, id, body) : catalogApi.create(path, body)),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKey(path) }),
    });
};

/** Sedes, facultades y programas ordenados por nombre, para la adscripción académica. */
export const useAffiliationCatalogs = () =>
    useQueries({
        queries: [CATALOG_PATHS.campuses, CATALOG_PATHS.faculties, CATALOG_PATHS.academicPrograms].map(listQuery),
        combine: ([campuses, faculties, programs]) => ({
            campuses: [...(campuses.data ?? [])].sort(byName),
            faculties: [...(faculties.data ?? [])].sort(byName),
            programs: [...(programs.data ?? [])].sort(byName),
            isPending: campuses.isPending || faculties.isPending || programs.isPending,
            error: campuses.error ?? faculties.error ?? programs.error,
        }),
    });
