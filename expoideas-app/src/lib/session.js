/**
 * Lo que la sesión guarda en localStorage: el token, el perfil que se muestra
 * en la cabecera y los pasos de primer ingreso pendientes. AuthProvider es quien
 * la cambia; apiClient solo lee el token.
 */

const KEYS = { token: 'token', user: 'user', pendingSteps: 'pendingSteps' };

const readJson = (key, fallback, isValid) => {
    try {
        const value = JSON.parse(localStorage.getItem(key));
        return isValid(value) ? value : fallback;
    } catch {
        return fallback;
    }
};

export const session = {
    token: () => localStorage.getItem(KEYS.token),

    /** @returns {{ id?: number, email?: string, firstName?: string, lastName?: string, photoId?: string } | null} */
    user: () => readJson(KEYS.user, null, (value) => value !== null && typeof value === 'object'),

    /** @returns {string[]} */
    pendingSteps: () => readJson(KEYS.pendingSteps, [], Array.isArray),

    save({ token, user, pendingSteps }) {
        if (token !== undefined) localStorage.setItem(KEYS.token, token);
        if (user !== undefined) localStorage.setItem(KEYS.user, JSON.stringify(user));
        if (pendingSteps !== undefined) {
            if (pendingSteps.length > 0) localStorage.setItem(KEYS.pendingSteps, JSON.stringify(pendingSteps));
            else localStorage.removeItem(KEYS.pendingSteps);
        }
    },

    /** Si esa clave de localStorage es de la sesión (para atender solo sus cambios desde otra pestaña). */
    owns: (key) => Object.values(KEYS).includes(key),

    clear() {
        Object.values(KEYS).forEach((key) => localStorage.removeItem(key));
    },
};
