/** Cuentas de ejemplo, con la forma que devuelve la API. */

export const ana = {
    id: 1,
    firstName: 'Ana María',
    lastName: 'Pérez',
    email: 'ana@unisimon.edu.co',
    role: 'STUDENT',
    campusId: 1,
    campus: 'Barranquilla',
    facultyId: 2,
    faculty: 'Ingeniería',
    pendingSteps: [],
};

export const luis = {
    id: 2,
    firstName: 'Luis',
    lastName: 'Gómez',
    email: 'luis@unisimon.edu.co',
    role: 'ADMIN',
    pendingSteps: [],
};

export const carla = {
    id: 3,
    firstName: 'Carla',
    lastName: 'Díaz',
    email: 'coordinacion@unisimon.edu.co',
    role: 'MACONDOLAB',
    pendingSteps: [],
};

/** Jurado externo recién creado: aún no cambia su contraseña temporal. */
export const marta = {
    id: 4,
    firstName: 'Marta',
    lastName: 'Ríos',
    email: 'marta@empresa.com',
    role: 'JUDGE',
    pendingSteps: ['CHANGE_PASSWORD'],
};

const CATALOGS = {
    '/campuses': [{ id: 1, name: 'Barranquilla' }],
    '/faculties': [{ id: 2, name: 'Ingeniería' }],
    '/academic-programs': [],
};

/** Respuesta de catalogApi.list por ruta, para simular los catálogos. */
export const catalogFor = (path) => CATALOGS[path] ?? [];

/** Edición con las inscripciones abiertas, como la devuelve la API. */
export const openEdition = {
    id: 1,
    name: 'Expoideas 2026-2',
    registrationOpensOn: '2026-11-03',
    registrationClosesOn: '2026-11-14',
    submissionClosesOn: '2026-11-28',
    registrationOpen: true,
    submissionOpen: true,
    tracks: [
        { track: 'INNPRENDE_I', minMembers: 2, maxMembers: 5 },
        { track: 'INNPRENDE_II', minMembers: 2, maxMembers: 5 },
    ],
};

/** Proyecto de Ana (id 1), que lo inscribió y todavía no tiene equipo. */
export const project = {
    id: 10,
    editionId: 1,
    edition: 'Expoideas 2026-2',
    track: 'INNPRENDE_I',
    title: 'BioSensor',
    summary: 'Sensores para detectar plagas antes de que se vean.',
    sectorId: 3,
    sector: 'Agroindustria y alimentos',
    teacherId: 7,
    teacher: 'Carlos Mendoza',
    registrationOpen: true,
    submissionOpen: true,
    minMembers: 2,
    maxMembers: 5,
    members: [{ userId: 1, fullName: 'Ana María Pérez', email: 'ana@unisimon.edu.co', teamRole: 'LEADER', status: 'ACCEPTED' }],
    createdAt: '2026-11-04T09:00:00',
};
