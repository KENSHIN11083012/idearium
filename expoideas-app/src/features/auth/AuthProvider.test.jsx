import { useEffect, useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { request } from '@/lib/apiClient';
import { createTestQueryClient, fakeJwt, jsonResponse } from '@/test/utils';
import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';

/** Última sesión que vio el consumidor, para llamar a login/completeStep desde la prueba. */
const session = { current: null };
function Consumer() {
    const auth = useAuth();
    useEffect(() => {
        session.current = auth;
    });
    return (
        <>
            <p>{auth.pendingSteps.join(',') || 'sin pendientes'}</p>
            <p>rol: {auth.role ?? 'ninguno'}</p>
            <p>cuenta: {auth.user?.email ?? 'nadie'}</p>
            <p>{auth.sessionExpired ? 'la sesión se cerró sola' : 'sin aviso de sesión cerrada'}</p>
            <HalfFilledForm />
        </>
    );
}

/** Algo a medio escribir en pantalla, como un formulario sin guardar. */
function HalfFilledForm() {
    const [text, setText] = useState('');
    return <input aria-label="A medio escribir" value={text} onChange={(event) => setText(event.target.value)} />;
}

/** Lo que deja en localStorage otra pestaña al entrar con una cuenta, con los avisos que recibe esta. */
const otherTabSignsIn = (role, email) => {
    const changes = {
        token: fakeJwt(role, email),
        user: JSON.stringify({ id: 7, email, firstName: 'Otra', lastName: 'Cuenta' }),
    };
    act(() => {
        Object.entries(changes).forEach(([key, value]) => {
            localStorage.setItem(key, value);
            window.dispatchEvent(new StorageEvent('storage', { key, newValue: value, storageArea: localStorage }));
        });
    });
};

const otherTabSignsOut = () => {
    act(() => {
        ['token', 'user', 'pendingSteps'].forEach((key) => {
            localStorage.removeItem(key);
            window.dispatchEvent(new StorageEvent('storage', { key, newValue: null, storageArea: localStorage }));
        });
    });
};

/**
 * Con sesión, AuthProvider consulta /users/me; salvo que la prueba diga otra
 * cosa, la API responde con ese perfil.
 */
const renderSession = (profile = {}) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(profile)));
    const queryClient = createTestQueryClient();
    render(
        <QueryClientProvider client={queryClient}>
            <AuthProvider>
                <Consumer />
            </AuthProvider>
        </QueryClientProvider>,
    );
    return queryClient;
};

afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
});

describe('Pasos de primer ingreso en la sesión', () => {
    it('el login los guarda y completarlos los quita, también de localStorage', () => {
        renderSession();

        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, ['CHANGE_PASSWORD', 'DATA_CONSENT']));
        expect(screen.getByText('CHANGE_PASSWORD,DATA_CONSENT')).toBeInTheDocument();
        expect(JSON.parse(localStorage.getItem('pendingSteps'))).toEqual(['CHANGE_PASSWORD', 'DATA_CONSENT']);

        act(() => session.current.completeStep('CHANGE_PASSWORD'));
        expect(screen.getByText('DATA_CONSENT')).toBeInTheDocument();

        act(() => session.current.completeStep('DATA_CONSENT'));
        expect(screen.getByText('sin pendientes')).toBeInTheDocument();
        expect(localStorage.getItem('pendingSteps')).toBeNull();
    });

    it('sobreviven a recargar la página', () => {
        localStorage.setItem('token', fakeJwt('JUDGE'));
        localStorage.setItem('pendingSteps', JSON.stringify(['DATA_CONSENT']));

        renderSession();

        expect(screen.getByText('DATA_CONSENT')).toBeInTheDocument();
    });

    it('un 403 de la API por primer ingreso los actualiza aunque la sesión ya estuviera al día', async () => {
        renderSession();
        act(() => session.current.login(fakeJwt('MACONDOLAB', 'carla@unisimon.edu.co'), { email: 'carla@unisimon.edu.co' }, []));
        expect(screen.getByText('sin pendientes')).toBeInTheDocument();

        vi.stubGlobal(
            'fetch',
            vi
                .fn()
                .mockResolvedValue(
                    jsonResponse(
                        { detail: 'Antes de continuar, completa tu primer ingreso.', pendingSteps: ['CHANGE_PASSWORD'] },
                        403,
                        'application/problem+json',
                    ),
                ),
        );

        await act(async () => {
            await expect(request('/admin/users')).rejects.toMatchObject({ status: 403 });
        });

        expect(screen.getByText('CHANGE_PASSWORD')).toBeInTheDocument();
    });

    it('cerrar sesión los borra y limpia la caché de datos', () => {
        const queryClient = renderSession();
        queryClient.setQueryData(['profile'], { firstName: 'Marta' });
        act(() => session.current.login(fakeJwt('JUDGE'), {}, ['DATA_CONSENT']));

        act(() => session.current.logout());

        expect(screen.getByText('sin pendientes')).toBeInTheDocument();
        expect(localStorage.getItem('pendingSteps')).toBeNull();
        expect(queryClient.getQueryData(['profile'])).toBeUndefined();
    });
});

describe('El rol de la sesión', () => {
    it('sale del token al entrar y, en cuanto responde /users/me, del perfil: un cambio de rol no exige cerrar sesión', async () => {
        renderSession({ id: 1, email: 'ana@unisimon.edu.co', role: 'TEACHER', pendingSteps: [] });

        act(() => session.current.login(fakeJwt('STUDENT', 'ana@unisimon.edu.co'), { email: 'ana@unisimon.edu.co' }, []));
        expect(screen.getByText('rol: STUDENT')).toBeInTheDocument();

        expect(await screen.findByText('rol: TEACHER')).toBeInTheDocument();
        expect(fetch).toHaveBeenCalledWith(expect.stringMatching(/\/users\/me$/), expect.anything());
    });

    it('se vuelve a consultar al volver a la pestaña', async () => {
        renderSession({ role: 'STUDENT' });
        act(() => session.current.login(fakeJwt('STUDENT', 'ana@unisimon.edu.co'), { email: 'ana@unisimon.edu.co' }, []));
        expect(await screen.findByText('rol: STUDENT')).toBeInTheDocument();

        fetch.mockResolvedValue(jsonResponse({ role: 'TEACHER' }));
        act(() => {
            window.dispatchEvent(new Event('visibilitychange'));
        });

        expect(await screen.findByText('rol: TEACHER')).toBeInTheDocument();
    });

    it('sin sesión no se consulta el perfil', () => {
        renderSession();

        expect(screen.getByText('rol: ninguno')).toBeInTheDocument();
        expect(fetch).not.toHaveBeenCalled();
    });
});

describe('La sesión que se cierra sola', () => {
    const unauthorized = () =>
        vi.stubGlobal(
            'fetch',
            vi.fn().mockResolvedValue(jsonResponse({ detail: 'Debes iniciar sesión' }, 401, 'application/problem+json')),
        );

    it('un 401 con la sesión abierta la cierra y queda dicho, para explicarlo al volver a entrar', async () => {
        renderSession();
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));
        expect(screen.getByText('sin aviso de sesión cerrada')).toBeInTheDocument();

        unauthorized();
        await act(async () => {
            await expect(request('/projects/10/evaluations/mine')).rejects.toMatchObject({ status: 401 });
        });

        expect(screen.getByText('cuenta: nadie')).toBeInTheDocument();
        expect(screen.getByText('la sesión se cerró sola')).toBeInTheDocument();
        expect(localStorage.getItem('token')).toBeNull();
    });

    it('al volver a entrar, el aviso se quita', async () => {
        renderSession();
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));
        unauthorized();
        await act(async () => {
            await expect(request('/users/me')).rejects.toMatchObject({ status: 401 });
        });

        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({})));
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));

        expect(screen.getByText('sin aviso de sesión cerrada')).toBeInTheDocument();
    });

    it('cerrar sesión a propósito no deja ese aviso', () => {
        renderSession();
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));

        act(() => session.current.logout());

        expect(screen.getByText('cuenta: nadie')).toBeInTheDocument();
        expect(screen.getByText('sin aviso de sesión cerrada')).toBeInTheDocument();
    });

    it('una contraseña equivocada al entrar tampoco', async () => {
        renderSession();
        unauthorized();

        await act(async () => {
            await expect(request('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({
                status: 401,
            });
        });

        expect(screen.getByText('sin aviso de sesión cerrada')).toBeInTheDocument();
    });

    it('abrir la página con el token ya vencido cuenta como sesión cerrada', () => {
        localStorage.setItem('token', fakeJwt('JUDGE', 'marta@empresa.com', -60));

        renderSession();

        expect(screen.getByText('cuenta: nadie')).toBeInTheDocument();
        expect(screen.getByText('la sesión se cerró sola')).toBeInTheDocument();
        expect(fetch).not.toHaveBeenCalled();
    });
});

describe('Varias pestañas con la misma sesión', () => {
    it('si en otra se cierra la sesión, aquí también, y se descartan los datos de la cuenta', () => {
        const queryClient = renderSession();
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));
        queryClient.setQueryData(['my-evaluations'], [{ projectId: 10 }]);

        otherTabSignsOut();

        expect(screen.getByText('cuenta: nadie')).toBeInTheDocument();
        expect(screen.getByText('rol: ninguno')).toBeInTheDocument();
        expect(screen.getByText('sin aviso de sesión cerrada')).toBeInTheDocument();
        expect(queryClient.getQueryData(['my-evaluations'])).toBeUndefined();
    });

    it('si en otra entra una cuenta distinta, aquí se cambia y lo que había en pantalla no pasa a la otra cuenta', () => {
        const queryClient = renderSession();
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));
        queryClient.setQueryData(['my-evaluations'], [{ projectId: 10 }]);
        fireEvent.change(screen.getByLabelText('A medio escribir'), { target: { value: 'Observación de Marta' } });

        otherTabSignsIn('TEACHER', 'pedro@unisimon.edu.co');

        expect(screen.getByText('cuenta: pedro@unisimon.edu.co')).toBeInTheDocument();
        expect(screen.getByText('rol: TEACHER')).toBeInTheDocument();
        expect(queryClient.getQueryData(['my-evaluations'])).toBeUndefined();
        expect(screen.getByLabelText('A medio escribir')).toHaveValue('');
    });

    it('si en otra entra la misma cuenta (o renueva el token), aquí nada se pierde', () => {
        const queryClient = renderSession();
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));
        queryClient.setQueryData(['my-evaluations'], [{ projectId: 10 }]);
        fireEvent.change(screen.getByLabelText('A medio escribir'), { target: { value: 'Observación de Marta' } });

        otherTabSignsIn('JUDGE', 'marta@empresa.com');

        expect(screen.getByText('cuenta: marta@empresa.com')).toBeInTheDocument();
        expect(queryClient.getQueryData(['my-evaluations'])).toEqual([{ projectId: 10 }]);
        expect(screen.getByLabelText('A medio escribir')).toHaveValue('Observación de Marta');
    });

    it('sin sesión aquí, entrar en otra pestaña la abre también en esta', () => {
        renderSession();

        otherTabSignsIn('STUDENT', 'ana@unisimon.edu.co');

        expect(screen.getByText('cuenta: ana@unisimon.edu.co')).toBeInTheDocument();
        expect(screen.getByText('rol: STUDENT')).toBeInTheDocument();
    });

    it('lo que otra pestaña guarde en localStorage y no sea de la sesión no la toca', () => {
        renderSession();
        act(() => session.current.login(fakeJwt('JUDGE'), { email: 'marta@empresa.com' }, []));

        act(() => {
            localStorage.setItem('expoideas:evaluation-draft:8:10', '{}');
            window.dispatchEvent(
                new StorageEvent('storage', {
                    key: 'expoideas:evaluation-draft:8:10',
                    newValue: '{}',
                    storageArea: localStorage,
                }),
            );
        });

        expect(screen.getByText('cuenta: marta@empresa.com')).toBeInTheDocument();
    });
});
