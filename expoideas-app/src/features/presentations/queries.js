import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { presentationApi } from './api';

const projectKey = (projectId) => ['presentation', String(projectId)];
const agendaKey = (editionId, track) => ['presentations', String(editionId), track];

/** La cita de un proyecto; null si no está programada. */
export const useProjectPresentation = (projectId) =>
    useQuery({
        queryKey: projectKey(projectId),
        queryFn: () => presentationApi.ofProject(projectId),
        enabled: Boolean(projectId),
    });

/** La agenda de una cátedra en una edición, en orden de fecha. */
export const usePresentationAgenda = (editionId, track) =>
    useQuery({
        queryKey: agendaKey(editionId, track),
        queryFn: () => presentationApi.agenda(editionId, track),
        enabled: Boolean(editionId && track),
    });

/** Programa o cambia (con body) o quita (sin body) la cita de un proyecto, y recarga la agenda. */
export const useSchedulePresentation = (editionId, track) => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ projectId, body }) =>
            body ? presentationApi.schedule(projectId, body) : presentationApi.cancel(projectId),
        onSuccess: (_, { projectId }) => {
            queryClient.invalidateQueries({ queryKey: agendaKey(editionId, track) });
            queryClient.invalidateQueries({ queryKey: projectKey(projectId) });
        },
    });
};
