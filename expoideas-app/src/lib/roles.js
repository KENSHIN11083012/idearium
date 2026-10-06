/**
 * Roles de Idearium. Los valores son los mismos que usa la API (enum Role) en el
 * JSON y en el claim "role" del token.
 *
 * Las reglas de permisos son espejo de Role en la API, que es quien decide de
 * verdad: aquí solo sirven para no ofrecer acciones que la API rechazaría.
 */

/** En orden de menor a mayor alcance, que es como se listan en los selectores. */
export const ROLES = {
    STUDENT: 'STUDENT',
    TEACHER: 'TEACHER',
    JUDGE: 'JUDGE',
    MACONDOLAB: 'MACONDOLAB',
    ADMIN: 'ADMIN',
};

export const ROLE_LABELS = {
    [ROLES.STUDENT]: 'Estudiante',
    [ROLES.TEACHER]: 'Profesor',
    [ROLES.JUDGE]: 'Jurado',
    [ROLES.MACONDOLAB]: 'MacondoLab',
    [ROLES.ADMIN]: 'Administrador',
};

/** Roles con acceso a la gestión de la plataforma (Usuarios y Catálogos). */
export const MANAGEMENT_ROLES = [ROLES.MACONDOLAB, ROLES.ADMIN];

const ALL_ROLES = Object.values(ROLES);

/** El rol si es uno conocido, o null. */
export const asRole = (value) => (ALL_ROLES.includes(value) ? value : null);

export const roleLabel = (role) => ROLE_LABELS[role] ?? 'Usuario';

/** Sin restricción (allowed vacío) basta con tener un rol. */
export const hasRole = (role, allowed) => {
    if (!allowed || allowed.length === 0) return true;
    return allowed.includes(role);
};

export const isManagement = (role) => MANAGEMENT_ROLES.includes(role);

/** Si el rol declara sede y facultad. Espejo de Role#requiresAffiliation. */
export const requiresAffiliation = (role) => role === ROLES.TEACHER || role === ROLES.STUDENT;

/**
 * Si `actor` puede administrar una cuenta con el rol `target` (cambiarle el rol
 * o la adscripción, restablecer su contraseña). Espejo de Role#canManage.
 */
export const canManage = (actor, target) => {
    if (actor === ROLES.ADMIN) return true;
    if (actor === ROLES.MACONDOLAB) return !isManagement(target);
    return false;
};

/** Roles que `actor` puede asignar o con los que puede crear cuentas. */
export const assignableRoles = (actor) => ALL_ROLES.filter((role) => canManage(actor, role));
