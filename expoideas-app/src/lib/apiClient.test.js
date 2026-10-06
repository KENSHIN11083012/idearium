import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '@/test/utils';
import { ONBOARDING_REQUIRED_EVENT, UNAUTHORIZED_EVENT, put, request, upload } from './apiClient';

afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
});

const listen = (eventName) => {
    const listener = vi.fn();
    window.addEventListener(eventName, listener);
    return { listener, stop: () => window.removeEventListener(eventName, listener) };
};

describe('apiClient', () => {
    it('envía objetos como JSON con su Content-Type y el token de la sesión', async () => {
        const fetch = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
        vi.stubGlobal('fetch', fetch);
        localStorage.setItem('token', 'abc');

        await put('/users/me', { firstName: 'Ana' });

        const [, options] = fetch.mock.calls[0];
        expect(options.headers['Content-Type']).toBe('application/json');
        expect(options.headers.Authorization).toBe('Bearer abc');
        expect(options.body).toBe('{"firstName":"Ana"}');
    });

    it('envía archivos como FormData sin fijar Content-Type (el navegador pone el boundary)', async () => {
        const fetch = vi.fn().mockResolvedValue(jsonResponse({ photoId: 'x' }));
        vi.stubGlobal('fetch', fetch);
        const form = new FormData();
        form.append('file', new File(['x'], 'yo.png', { type: 'image/png' }));

        await put('/users/me/photo', form);

        const [, options] = fetch.mock.calls[0];
        expect(options.body).toBe(form);
        expect(options.headers).not.toHaveProperty('Content-Type');
    });

    it('un error trae el mensaje de la API, su estado y el cuerpo', async () => {
        const body = { detail: 'Datos inválidos', fields: { email: 'El correo debe terminar en @unisimon.edu.co' } };
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(body, 400, 'application/problem+json')));

        await expect(request('/auth/register', { method: 'POST', body: {} })).rejects.toMatchObject({
            status: 400,
            message: 'Datos inválidos',
            body,
        });
    });

    it('sin detalle usa un mensaje según el estado', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 413, headers: { get: () => 'text/html' } }));

        await expect(request('/users/me/photo')).rejects.toMatchObject({
            status: 413,
            message: 'El archivo supera el tamaño máximo permitido de 5 MB.',
        });
    });

    it('un 429 de Nginx, que llega sin JSON, se explica como demasiados intentos', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 429, headers: { get: () => 'text/html' } }));

        await expect(request('/auth/login')).rejects.toMatchObject({
            status: 429,
            message: 'Demasiados intentos seguidos. Espera un momento y vuelve a intentarlo.',
        });
    });

    it('sin red lanza un error con estado 0', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

        await expect(request('/users/me')).rejects.toMatchObject({ status: 0, message: 'Error de red. Verifica tu conexión.' });
    });

    it('un 401 con sesión avisa para cerrarla', async () => {
        const { listener, stop } = listen(UNAUTHORIZED_EVENT);
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ detail: 'Debes iniciar sesión' }, 401)));
        localStorage.setItem('token', 'abc');

        await expect(request('/users/me')).rejects.toMatchObject({ status: 401 });

        expect(listener).toHaveBeenCalledTimes(1);
        stop();
    });

    it('un 401 sin sesión (la contraseña equivocada al entrar) no avisa de ninguna sesión vencida', async () => {
        const { listener, stop } = listen(UNAUTHORIZED_EVENT);
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ detail: 'Credenciales inválidas' }, 401)));
        // Queda el token de una sesión anterior, pero el inicio de sesión no lo envía.
        localStorage.setItem('token', 'abc');

        await expect(request('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({
            status: 401,
            message: 'Credenciales inválidas',
        });

        expect(listener).not.toHaveBeenCalled();
        stop();
    });

    it('un 403 por primer ingreso avisa con los pasos pendientes', async () => {
        const { listener, stop } = listen(ONBOARDING_REQUIRED_EVENT);
        vi.stubGlobal(
            'fetch',
            vi
                .fn()
                .mockResolvedValue(
                    jsonResponse(
                        { detail: 'Antes de continuar, completa tu primer ingreso.', pendingSteps: ['CHANGE_PASSWORD'] },
                        403,
                    ),
                ),
        );

        await expect(request('/admin/users')).rejects.toMatchObject({ status: 403 });

        expect(listener.mock.calls[0][0].detail).toEqual(['CHANGE_PASSWORD']);
        stop();
    });

    it('un 403 por falta de rol no toca el primer ingreso', async () => {
        const { listener, stop } = listen(ONBOARDING_REQUIRED_EVENT);
        vi.stubGlobal(
            'fetch',
            vi.fn().mockResolvedValue(jsonResponse({ detail: 'No tienes permiso para realizar esta acción' }, 403)),
        );

        await expect(request('/admin/users')).rejects.toThrow('No tienes permiso para realizar esta acción');

        expect(listener).not.toHaveBeenCalled();
        stop();
    });
});

describe('subir con avance', () => {
    /** Un XMLHttpRequest de mentira: la prueba decide cuándo avanza y cómo responde. */
    class FakeXhr {
        static last = null;
        headers = {};
        upload = {};
        constructor() {
            FakeXhr.last = this;
        }
        open(method, url) {
            Object.assign(this, { method, url });
        }
        setRequestHeader(name, value) {
            this.headers[name] = value;
        }
        getResponseHeader() {
            return this.contentType;
        }
        send(body) {
            this.body = body;
        }
        respond(status, body, contentType = 'application/json') {
            Object.assign(this, { status, responseText: JSON.stringify(body), contentType });
            this.onload();
        }
    }

    const start = (onProgress) => {
        vi.stubGlobal('XMLHttpRequest', FakeXhr);
        localStorage.setItem('token', 'abc');
        const form = new FormData();
        form.append('file', new Blob(['%PDF']), 'poster.pdf');
        const pending = upload('/projects/10/deliverables?deliverableTypeId=5', form, { onProgress });
        return { pending, xhr: FakeXhr.last, form };
    };

    it('envía el archivo con el token y devuelve lo que responde la API', async () => {
        const { pending, xhr, form } = start();

        expect(xhr.method).toBe('POST');
        expect(xhr.url).toMatch(/\/projects\/10\/deliverables\?deliverableTypeId=5$/);
        expect(xhr.headers.Authorization).toBe('Bearer abc');
        // Sin Content-Type: el navegador pone el del multipart con su boundary.
        expect(xhr.headers['Content-Type']).toBeUndefined();
        expect(xhr.body).toBe(form);

        xhr.respond(200, [{ type: { id: 5 } }]);
        await expect(pending).resolves.toEqual([{ type: { id: 5 } }]);
    });

    it('avisa de cuánto va mientras sube', async () => {
        const onProgress = vi.fn();
        const { pending, xhr } = start(onProgress);

        xhr.upload.onprogress({ lengthComputable: true, loaded: 1, total: 4 });
        xhr.upload.onprogress({ lengthComputable: false, loaded: 2, total: 0 });
        xhr.upload.onprogress({ lengthComputable: true, loaded: 4, total: 4 });
        xhr.respond(200, []);
        await pending;

        expect(onProgress.mock.calls).toEqual([[0.25], [1]]);
    });

    it('un rechazo de la API sale igual que en cualquier otra petición, con sus campos', async () => {
        const { pending, xhr } = start();

        xhr.respond(
            400,
            { detail: 'Los datos enviados no son válidos.', fields: { file: 'Debe ser un PDF' } },
            'application/problem+json',
        );

        await expect(pending).rejects.toMatchObject({
            status: 400,
            message: 'Los datos enviados no son válidos.',
            body: { fields: { file: 'Debe ser un PDF' } },
        });
    });

    it('un 401 mientras sube también cierra la sesión', async () => {
        const { listener, stop } = listen(UNAUTHORIZED_EVENT);
        const { pending, xhr } = start();

        xhr.respond(401, { detail: 'Debes iniciar sesión' }, 'application/problem+json');

        await expect(pending).rejects.toMatchObject({ status: 401 });
        expect(listener).toHaveBeenCalledTimes(1);
        stop();
    });

    it('sin red lanza el mismo error que una petición normal', async () => {
        const { pending, xhr } = start();

        xhr.onerror();

        await expect(pending).rejects.toMatchObject({ status: 0, message: 'Error de red. Verifica tu conexión.' });
    });
});
