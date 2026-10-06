import { get, post, put } from '@/lib/apiClient';

/** Rutas de los catálogos en la API. La lectura es pública: el registro la usa sin sesión. */
export const CATALOG_PATHS = {
    campuses: '/campuses',
    faculties: '/faculties',
    academicPrograms: '/academic-programs',
    sectors: '/sectors',
    prototypeTypes: '/prototype-types',
    keywords: '/keywords',
};

export const catalogApi = {
    list: (path) => get(path, { auth: false }),
    create: (path, body) => post(path, body),
    update: (path, id, body) => put(`${path}/${id}`, body),
};
