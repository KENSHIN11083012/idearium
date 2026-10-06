/** Iniciales para avatares: nombres "Ana María" y apellidos "Pérez Gómez" -> "AP". */
export function initials(firstName = '', lastName = '') {
    const letters = `${firstName?.trim()[0] ?? ''}${lastName?.trim()[0] ?? ''}`;
    return letters.toUpperCase() || '?';
}

/** Nombre para mostrar: "Ana María Pérez Gómez". */
export function fullName(firstName, lastName) {
    return [firstName, lastName].filter(Boolean).join(' ');
}

/** Minúsculas y sin tildes, para búsquedas: "Ingeniería" -> "ingenieria". */
export function normalizeText(text = '') {
    return text
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLowerCase();
}

/** Orden alfabético en español, para listas de catálogos. */
export const byName = (a, b) => a.name.localeCompare(b.name, 'es');
