import { del, get, post } from '@/lib/apiClient';

/** Jurados por proyecto: la gestión asigna; cada persona consulta los proyectos que le tocan. */
export const juryApi = {
    listJurors: (projectId) => get(`/projects/${projectId}/jurors`),
    assign: (projectId, email) => post(`/projects/${projectId}/jurors`, { email }),
    remove: (projectId, userId) => del(`/projects/${projectId}/jurors/${userId}`),
    myProjects: () => get('/jury/projects'),
};
