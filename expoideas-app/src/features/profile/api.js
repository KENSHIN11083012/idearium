import { del, get, put } from '@/lib/apiClient';
import { FILE_PART } from '@/lib/files';

/** La propia cuenta: la API la identifica por la sesión, nunca por un id del cliente. */
export const profileApi = {
    get: () => get('/users/me'),
    update: (body) => put('/users/me', body),
    uploadPhoto: (file) => {
        const form = new FormData();
        form.append(FILE_PART, file);
        return put('/users/me/photo', form);
    },
    deletePhoto: () => del('/users/me/photo'),
};
