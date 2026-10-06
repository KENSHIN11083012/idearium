import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { projectApi } from './api';

const projectsKey = ['projects'];
const invitationsKey = ['invitations'];
const projectKey = (id) => ['projects', String(id)];

/** Mis proyectos, incluidos aquellos a los que me invitaron. */
export const useMyProjects = () => useQuery({ queryKey: projectsKey, queryFn: projectApi.listMine });

export const useProject = (id) => useQuery({ queryKey: projectKey(id), queryFn: () => projectApi.get(id), enabled: Boolean(id) });

/** Invitaciones sin responder. */
export const useMyInvitations = () => useQuery({ queryKey: invitationsKey, queryFn: projectApi.listInvitations });

/** Docentes para elegir el del grupo. */
export const useTeachers = () => useQuery({ queryKey: ['teachers'], queryFn: projectApi.listTeachers });

/** Tras cualquier cambio se recargan los proyectos y las invitaciones. */
const useTeamMutation = (mutationFn) => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: projectsKey });
            queryClient.invalidateQueries({ queryKey: invitationsKey });
        },
    });
};

/** Inscribe (sin id) o edita (con id) un proyecto. */
export const useSaveProject = () =>
    useTeamMutation(({ id, body }) => (id ? projectApi.update(id, body) : projectApi.create(body)));

export const useDeleteProject = () => useTeamMutation((id) => projectApi.remove(id));

export const useInviteMember = (projectId) => useTeamMutation((email) => projectApi.invite(projectId, email));

export const useRemoveMember = (projectId) => useTeamMutation((userId) => projectApi.removeMember(projectId, userId));

/** Guarda el resultado y deja el proyecto devuelto en la caché; el listado de la gestión se recarga. */
export const useSetResult = (projectId) => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (result) => projectApi.setResult(projectId, result),
        onSuccess: (project) => {
            queryClient.setQueryData(projectKey(projectId), project);
            queryClient.invalidateQueries({ queryKey: ['project-directory'] });
        },
    });
};

export const useAnswerInvitation = () =>
    useTeamMutation(({ id, accept }) => (accept ? projectApi.acceptInvitation(id) : projectApi.declineInvitation(id)));
