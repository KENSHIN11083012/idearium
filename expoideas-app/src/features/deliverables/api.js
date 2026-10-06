import { del, get, post, put, request, upload } from '@/lib/apiClient';
import { FILE_PART } from '@/lib/files';

/** Entregables: lo que pide cada cátedra y lo que sube cada proyecto. */
const TYPES = '/deliverable-types';

export const deliverableApi = {
    listTypes: (editionId, track) => get(`${TYPES}?editionId=${editionId}&track=${track}`),
    createType: (body) => post(TYPES, body),
    updateType: (id, body) => put(`${TYPES}/${id}`, body),
    removeType: (id) => del(`${TYPES}/${id}`),
    /** La plantilla (PDF, DOCX o PPTX) que el equipo descarga y diligencia. */
    uploadTemplate: (id, file) => {
        const body = new FormData();
        body.append(FILE_PART, file);
        return request(`${TYPES}/${id}/template`, { method: 'PUT', body });
    },
    removeTemplate: (id) => del(`${TYPES}/${id}/template`),

    listOfProject: (projectId) => get(`/projects/${projectId}/deliverables`),
    /** El navegador arma el multipart; el tipo de entregable va en la URL. */
    upload: (projectId, deliverableTypeId, file, onProgress) => {
        const body = new FormData();
        body.append(FILE_PART, file);
        return upload(`/projects/${projectId}/deliverables?deliverableTypeId=${deliverableTypeId}`, body, { onProgress });
    },
    /** Un enlace para un entregable de tipo LINK. */
    submitLink: (projectId, body) => post(`/projects/${projectId}/deliverables/links`, body),
    remove: (projectId, deliverableId) => del(`/projects/${projectId}/deliverables/${deliverableId}`),
};
