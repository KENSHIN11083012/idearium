import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { editionApi } from './api';

const editionsKey = ['editions'];

/** Ediciones, de la más reciente a la más antigua (las ordena la API). */
export const useEditions = () => useQuery({ queryKey: editionsKey, queryFn: editionApi.list });

/** Publica (publish: true) u oculta las notas de una cátedra: `{ id, track, publish }`. */
export const usePublishGrades = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, track, publish }) =>
            publish ? editionApi.publishGrades(id, track) : editionApi.hideGrades(id, track),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: editionsKey }),
    });
};

/** Crea (sin id) o edita (con id) una edición y recarga la lista. */
export const useSaveEdition = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, body }) => (id ? editionApi.update(id, body) : editionApi.create(body)),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: editionsKey }),
    });
};
