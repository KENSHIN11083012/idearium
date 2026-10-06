import { del, get, put } from '@/lib/apiClient';

/** Sustentaciones: la gestión programa; el equipo y el profesor consultan la de su proyecto. */
export const presentationApi = {
    /** null si el proyecto todavía no tiene cita (la API responde 204). */
    ofProject: (projectId) => get(`/projects/${projectId}/presentation`),
    schedule: (projectId, body) => put(`/projects/${projectId}/presentation`, body),
    cancel: (projectId) => del(`/projects/${projectId}/presentation`),
    agenda: (editionId, track) => get(`/presentations?editionId=${editionId}&track=${track}`),
};
