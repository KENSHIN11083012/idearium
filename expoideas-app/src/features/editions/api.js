import { del, get, post, put } from '@/lib/apiClient';

/** Ediciones de la Expo. La lectura es pública: las fechas se anuncian sin sesión. */
const PATH = '/editions';

export const editionApi = {
    list: () => get(PATH, { auth: false }),
    create: (body) => post(PATH, body),
    update: (id, body) => put(`${PATH}/${id}`, body),
    /** Desde ahora los equipos de esa cátedra ven su nota. */
    publishGrades: (id, track) => put(`${PATH}/${id}/tracks/${track}/grades-publication`),
    hideGrades: (id, track) => del(`${PATH}/${id}/tracks/${track}/grades-publication`),
};
