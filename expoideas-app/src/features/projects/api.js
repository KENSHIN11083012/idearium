import { del, get, post, put } from '@/lib/apiClient';
import { toQuery } from '@/lib/query';

/** Proyectos, equipo e invitaciones. Todo pide sesión. */
const PROJECTS = '/projects';
const INVITATIONS = '/invitations';

export const projectApi = {
    listMine: () => get(`${PROJECTS}/mine`),
    get: (id) => get(`${PROJECTS}/${id}`),
    create: (body) => post(PROJECTS, body),
    update: (id, body) => put(`${PROJECTS}/${id}`, body),
    remove: (id) => del(`${PROJECTS}/${id}`),
    /** A quién: `{ email }`, o `{ userId }` si se eligió en la búsqueda por nombre. */
    invite: (id, invitee) => post(`${PROJECTS}/${id}/invitations`, invitee),
    /** Estudiantes que el líder puede invitar y cuyo nombre contiene ese texto. */
    searchCandidates: (id, search) => get(`${PROJECTS}/${id}/invitation-candidates${toQuery({ search })}`),
    removeMember: (id, userId) => del(`${PROJECTS}/${id}/members/${userId}`),
    /** El resultado, por el profesor del grupo o la gestión tras el cierre de entregas. */
    setResult: (id, result) => put(`${PROJECTS}/${id}/result`, { result }),
    listInvitations: () => get(INVITATIONS),
    /** Quien acepta dice el semestre que cursa. */
    acceptInvitation: (id, semester) => post(`${INVITATIONS}/${id}/acceptance`, { semester }),
    declineInvitation: (id) => post(`${INVITATIONS}/${id}/rejection`),
    listTeachers: () => get('/teachers'),
};
