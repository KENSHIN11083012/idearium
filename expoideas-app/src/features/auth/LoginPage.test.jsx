import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ROUTES } from '@/lib/routes';
import { apiError } from '@/test/utils';
import LoginPage from './LoginPage';
import { authApi } from './api';
import { useAuth } from './useAuth';

vi.mock('./useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('./api', () => ({ authApi: { login: vi.fn() } }));

function renderLogin({ sessionExpired = false } = {}) {
    const login = vi.fn();
    useAuth.mockReturnValue({ login, sessionExpired });
    render(
        <MemoryRouter initialEntries={[ROUTES.LOGIN]}>
            <Routes>
                <Route path={ROUTES.LOGIN} element={<LoginPage />} />
                <Route path={ROUTES.ONBOARDING} element={<p>Pantalla de primer ingreso</p>} />
                <Route path={ROUTES.USERS} element={<p>Pantalla de usuarios</p>} />
                <Route path={ROUTES.HOME} element={<p>Inicio</p>} />
            </Routes>
        </MemoryRouter>,
    );
    return login;
}

async function signIn(user, email) {
    await user.type(screen.getByLabelText(/^Correo institucional/), email);
    await user.type(screen.getByLabelText(/^Contraseña/), 'Temporal#2026');
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
}

describe('Inicio de sesión', () => {
    it('con pasos de primer ingreso pendientes lleva a resolverlos', async () => {
        const user = userEvent.setup();
        authApi.login.mockResolvedValue({
            token: 'jwt',
            role: 'MACONDOLAB',
            id: 3,
            firstName: 'Carla',
            lastName: 'Díaz',
            photoId: null,
            pendingSteps: ['CHANGE_PASSWORD', 'DATA_CONSENT'],
        });
        const login = renderLogin();

        await signIn(user, 'coordinacion@unisimon.edu.co');

        expect(login).toHaveBeenCalledWith(
            'jwt',
            // El id va en la sesión: con él la app sabe cuál integrante de un equipo es quien mira.
            { id: 3, email: 'coordinacion@unisimon.edu.co', firstName: 'Carla', lastName: 'Díaz', photoId: null },
            ['CHANGE_PASSWORD', 'DATA_CONSENT'],
        );
        expect(await screen.findByText('Pantalla de primer ingreso')).toBeInTheDocument();
    });

    it('sin pendientes va al inicio de su rol', async () => {
        const user = userEvent.setup();
        authApi.login.mockResolvedValue({
            token: 'jwt',
            role: 'MACONDOLAB',
            id: 3,
            firstName: 'Carla',
            lastName: 'Díaz',
            photoId: null,
            pendingSteps: [],
        });
        renderLogin();

        await signIn(user, 'coordinacion@unisimon.edu.co');

        expect(await screen.findByText('Pantalla de usuarios')).toBeInTheDocument();
    });

    it('un error de credenciales se muestra como error general', async () => {
        const user = userEvent.setup();
        authApi.login.mockRejectedValue(apiError('Credenciales inválidas', 401));
        renderLogin();

        await signIn(user, 'coordinacion@unisimon.edu.co');

        expect(await screen.findByText('Credenciales inválidas')).toBeInTheDocument();
    });

    it('si la sesión se cerró sola, lo explica', () => {
        renderLogin({ sessionExpired: true });

        expect(screen.getByText('Tu sesión se cerró')).toBeInTheDocument();
        expect(screen.getByText('Vuelve a entrar para seguir donde ibas.')).toBeInTheDocument();
    });

    it('quien llega a iniciar sesión por su cuenta no ve ese aviso', () => {
        renderLogin();

        expect(screen.queryByText('Tu sesión se cerró')).not.toBeInTheDocument();
    });
});
