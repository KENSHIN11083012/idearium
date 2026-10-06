import { z } from 'zod';

/**
 * Reglas de validación compartidas por los formularios. Son las mismas que
 * aplica la API (ValidationPatterns): si cambian allá, hay que cambiarlas aquí.
 */

/** 8 a 72 caracteres, con al menos un número y un símbolo. */
const PASSWORD_REGEX = /^(?=.*\d)(?=.*[\W_]).{8,72}$/;

/**
 * La API guarda la contraseña con BCrypt, que solo lee los primeros 72 bytes: el
 * tope se cuenta en bytes, y una tilde o una ñ ocupan dos.
 */
const PASSWORD_MAX_BYTES = 72;
const fitsInBytes = (value) => new TextEncoder().encode(value).length <= PASSWORD_MAX_BYTES;

/** Corto a propósito: PasswordRequirements ya muestra el detalle bajo el campo. */
const PASSWORD_MESSAGE = 'La contraseña no cumple los requisitos';

/** Requisitos de la contraseña, para mostrarlos como lista mientras se escribe. */
export const PASSWORD_REQUIREMENTS = [
    {
        text: 'Entre 8 y 72 caracteres (las tildes y la ñ cuentan por dos)',
        met: (value) => value.length >= 8 && fitsInBytes(value),
    },
    { text: 'Al menos un número', met: (value) => /\d/.test(value) },
    { text: 'Al menos un símbolo', met: (value) => /[\W_]/.test(value) },
];

export const INSTITUTIONAL_DOMAIN = '@unisimon.edu.co';
const INSTITUTIONAL_EMAIL_REGEX = /^[^@\s]+@unisimon\.edu\.co$/i;

// ── Reglas zod reutilizables ─────────────────────────────────────────────

const name = (missing) => z.string().trim().min(1, missing).max(100, 'Máximo 100 caracteres');

export const rules = {
    firstName: (missing = 'Ingresa tus nombres') => name(missing),
    lastName: (missing = 'Ingresa tus apellidos') => name(missing),
    /** Correo @unisimon.edu.co, recortando espacios. */
    institutionalEmail: (
        message = `Usa tu correo institucional (${INSTITUTIONAL_DOMAIN})`,
        missing = 'Ingresa tu correo institucional',
    ) => z.string().trim().min(1, missing).max(150, 'Máximo 150 caracteres').regex(INSTITUTIONAL_EMAIL_REGEX, message),
    /** Cualquier correo válido (jurados externos). */
    anyEmail: () =>
        z.string().trim().min(1, 'Ingresa el correo').max(150, 'Máximo 150 caracteres').pipe(z.email('Ingresa un correo válido')),
    password: () => z.string().regex(PASSWORD_REGEX, PASSWORD_MESSAGE).refine(fitsInBytes, PASSWORD_MESSAGE),
    dataConsent: (message) => z.boolean().refine((value) => value === true, { message }),
};

/** Contraseña nueva con su confirmación; añade el refine `passwordsMatch` al objeto. */
export const newPasswordShape = {
    newPassword: rules.password(),
    confirmPassword: z.string().min(1, 'Confirma la nueva contraseña'),
};

export const passwordsMatch = [
    (values) => values.newPassword === values.confirmPassword,
    { message: 'Las contraseñas no coinciden', path: ['confirmPassword'] },
];

// ── Errores de la API ────────────────────────────────────────────────────

/**
 * Reparte los errores de la API en un formulario. Los de un 400 por campo
 * ({ fields: {...} }) van junto a su campo; los formularios usan los mismos
 * nombres que la API.
 *
 * Lo que no tiene dónde ir no se pierde: un error sin campos, o el de un campo
 * que este formulario no pinta (la API valida algo que aquí no se pregunta), se
 * devuelve para mostrarlo como error general.
 *
 * @param {Error & { body?: { fields?: Record<string, string> } }} error
 * @param {(field: string, error: { type: string, message: string }, options?: object) => void} setError
 * @param {object} [control] el de react-hook-form: sabe qué campos están montados en el
 *   formulario. Sin él, se da por hecho que están todos.
 * @returns {string | null} lo que queda por mostrar como error general, o null si todo quedó en su campo
 */
export const placeServerErrors = (error, setError, control) => {
    const fields = error?.body?.fields;
    const entries = fields && typeof fields === 'object' ? Object.entries(fields).filter(([, message]) => Boolean(message)) : [];
    if (entries.length === 0) return error.message;

    // Los campos montados (con register o con Controller). Es estado interno de
    // react-hook-form: no hay forma pública de preguntarlo, y las pruebas de los
    // formularios fallan si un cambio de versión lo mueve.
    const mounted = control?._names?.mount;
    const hasField = (field) => !mounted || mounted.has(field);

    let focused = false;
    const unplaced = [];
    entries.forEach(([field, message]) => {
        if (hasField(field)) {
            setError(field, { type: 'server', message }, { shouldFocus: !focused });
            focused = true;
        } else {
            unplaced.push(message);
        }
    });
    return unplaced.length > 0 ? [...new Set(unplaced)].join(' ') : null;
};

/**
 * Manejo estándar del error de un formulario: errores por campo junto a cada
 * campo; el resto, como error general (errors.root).
 */
export const handleFormError = (error, setError, control) => {
    const general = placeServerErrors(error, setError, control);
    if (general) setError('root', { type: 'server', message: general });
};
