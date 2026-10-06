import { session } from '@/lib/session';

/**
 * Cliente HTTP único de la app: arma las cabeceras, adjunta el token de la
 * sesión y traduce los errores de la API a mensajes para el usuario. Las
 * subidas de archivos con avance van por `upload`.
 */

export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

/**
 * Se dispara cuando la API responde 401 a una petición que llevaba el token de
 * la sesión: AuthProvider lo escucha y la cierra. Un 401 sin token (la
 * contraseña equivocada al entrar) no es una sesión vencida.
 */
export const UNAUTHORIZED_EVENT = 'expoideas:unauthorized';

/**
 * Se dispara cuando la API responde 403 porque la cuenta tiene pasos de primer
 * ingreso sin completar; el detalle trae la lista. AuthProvider la guarda y las
 * rutas protegidas llevan al primer ingreso.
 */
export const ONBOARDING_REQUIRED_EVENT = 'expoideas:onboarding-required';

const MESSAGES_BY_STATUS = {
    400: 'Los datos enviados no son válidos.',
    401: 'Tu sesión expiró. Inicia sesión nuevamente.',
    403: 'No tienes permiso para realizar esta acción.',
    404: 'El recurso no fue encontrado.',
    409: 'El recurso ya existe.',
    413: 'El archivo supera el tamaño máximo permitido de 5 MB.',
    // Lo responde Nginx, sin cuerpo JSON, cuando frena los intentos de acceso en serie.
    429: 'Demasiados intentos seguidos. Espera un momento y vuelve a intentarlo.',
};

/**
 * Los errores de la API vienen en formato Problem Details (RFC 9457): el texto
 * para el usuario está en `detail`, y en los 400 de validación `fields` trae el
 * mensaje de cada campo (ver applyServerErrors en lib/validation.js).
 */
const errorMessage = (status, body) =>
    body?.detail ||
    MESSAGES_BY_STATUS[status] ||
    (status >= 500 ? 'Error del servidor. Intenta nuevamente más tarde.' : `Error inesperado (${status}).`);

/** application/json y también application/problem+json, el tipo de los errores. */
const isJson = (contentType) => /application\/([\w.-]+\+)?json/.test(contentType);

const parseBody = async (response) => {
    if (response.status === 204) return null;
    const contentType = response.headers.get('content-type') || '';
    if (!isJson(contentType)) return null;
    return response.json().catch(() => null);
};

/** El fallo de una respuesta de la API, con los avisos que dependen de su código. */
const failure = (status, body, token) => {
    // Sin token no había sesión que cerrar: es la contraseña equivocada al entrar.
    if (status === 401 && token) {
        window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    const pendingSteps = body?.pendingSteps;
    if (status === 403 && Array.isArray(pendingSteps) && pendingSteps.length > 0) {
        window.dispatchEvent(new CustomEvent(ONBOARDING_REQUIRED_EVENT, { detail: pendingSteps }));
    }
    const error = new Error(errorMessage(status, body));
    error.status = status;
    error.body = body;
    return error;
};

const networkFailure = () => {
    const error = new Error('Error de red. Verifica tu conexión.');
    error.status = 0;
    return error;
};

/**
 * @param {string} path  ruta relativa a la API, p. ej. '/users/me'
 * @param {object} options  method, body (objeto plano o FormData) y auth (por defecto true)
 * @throws {Error} con .status y, si lo hubo, .body
 */
export const request = async (path, { method = 'GET', body, auth = true } = {}) => {
    const token = auth ? session.token() : null;
    // Con FormData (archivos) el navegador arma el Content-Type multipart con su boundary.
    const isForm = body instanceof FormData;

    let response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            method,
            headers: {
                Accept: 'application/json',
                ...(body !== undefined && !isForm ? { 'Content-Type': 'application/json' } : {}),
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            ...(body !== undefined ? { body: isForm ? body : JSON.stringify(body) } : {}),
        });
    } catch {
        throw networkFailure();
    }

    const responseBody = await parseBody(response);
    if (!response.ok) throw failure(response.status, responseBody, token);
    return responseBody;
};

/**
 * Sube un archivo avisando de cuánto va. fetch no dice el avance de lo que
 * envía, así que las subidas van con XMLHttpRequest; los errores salen igual
 * que en request.
 *
 * @param {string} path  ruta relativa a la API
 * @param {FormData} body
 * @param {(fraction: number) => void} [options.onProgress] recibe de 0 a 1 mientras sube
 */
export const upload = (path, body, { method = 'POST', onProgress } = {}) =>
    new Promise((resolve, reject) => {
        const token = session.token();
        const xhr = new XMLHttpRequest();
        xhr.open(method, `${API_BASE_URL}${path}`);
        xhr.setRequestHeader('Accept', 'application/json');
        if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
        xhr.upload.onprogress = (event) => {
            if (event.lengthComputable) onProgress?.(event.loaded / event.total);
        };
        xhr.onerror = () => reject(networkFailure());
        xhr.onload = () => {
            let responseBody = null;
            if (xhr.status !== 204 && isJson(xhr.getResponseHeader('content-type') || '')) {
                try {
                    responseBody = JSON.parse(xhr.responseText);
                } catch {
                    responseBody = null;
                }
            }
            if (xhr.status >= 200 && xhr.status < 300) resolve(responseBody);
            else reject(failure(xhr.status, responseBody, token));
        };
        xhr.send(body);
    });

export const get = (path, options) => request(path, { ...options, method: 'GET' });
export const post = (path, body, options) => request(path, { ...options, method: 'POST', body });
export const put = (path, body, options) => request(path, { ...options, method: 'PUT', body });
export const del = (path, options) => request(path, { ...options, method: 'DELETE' });
