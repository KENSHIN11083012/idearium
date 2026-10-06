/**
 * Las dos cátedras de la Cátedra UNISIMÓN INNPRENDE. Los valores son los mismos
 * que usa la API (enum Track); las etiquetas son las que se muestran.
 *
 * Un proyecto se inscribe en una cátedra y, si el grupo vuelve a participar, se
 * inscribe otra vez por separado: no es el mismo proyecto que avanza.
 */

const TRACKS = {
    INNPRENDE_I: 'INNPRENDE_I',
    INNPRENDE_II: 'INNPRENDE_II',
};

export const TRACK_LIST = [TRACKS.INNPRENDE_I, TRACKS.INNPRENDE_II];

/** La cátedra sola: para las reglas (aprobar INNPRENDE I, cupos por cátedra). */
export const TRACK_COURSES = {
    [TRACKS.INNPRENDE_I]: 'INNPRENDE I',
    [TRACKS.INNPRENDE_II]: 'INNPRENDE II',
};

/** La cátedra con el nombre de su muestra: Despegue (antes Expoideas 1) y Aterrizaje (antes Expoideas 2). */
export const TRACK_LABELS = {
    [TRACKS.INNPRENDE_I]: 'INNPRENDE I · Despegue',
    [TRACKS.INNPRENDE_II]: 'INNPRENDE II · Aterrizaje',
};

/** Qué se entrega en cada cátedra, para orientar a quien configura la edición. */
export const TRACK_DESCRIPTIONS = {
    [TRACKS.INNPRENDE_I]: 'Investigación e innovación: el entregable final es un póster.',
    [TRACKS.INNPRENDE_II]: 'Emprendimiento y prototipado: prototipo y pitch ante jurados.',
};

export const trackLabel = (track) => TRACK_LABELS[track] ?? track;

export const trackCourse = (track) => TRACK_COURSES[track] ?? track;
