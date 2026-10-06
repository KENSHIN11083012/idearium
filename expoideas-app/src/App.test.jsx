import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { useAuth } from '@/features/auth/useAuth';
import { ROLES } from '@/lib/roles';
import { ROUTES } from '@/lib/routes';
import { ana, catalogFor } from '@/test/fixtures';
import { createTestQueryClient, jsonResponse, sessionFor } from '@/test/utils';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

/**
 * Prueba de humo del enrutador real: cada URL muestra su página y los guardias
 * redirigen según la sesión, el rol y los pasos de primer ingreso.
 */

const BASE = import.meta.env.BASE_URL.replace(/\/$/, '');

const HOME = /Las ideas de la Unisimón/;
const LOGIN = 'Bienvenido de nuevo';

const visit = (route) => {
    window.history.pushState({}, '', `${BASE}${route}`);
    render(
        <QueryClientProvider client={createTestQueryClient()}>
            <App />
        </QueryClientProvider>,
    );
};

// Las páginas se cargan con lazy(): la primera vez tardan más que el segundo por defecto.
const expectPage = async (title, route) => {
    expect(await screen.findByRole('heading', { level: 1, name: title }, { timeout: 5000 })).toBeInTheDocument();
    expect(window.location.pathname).toBe(`${BASE}${route}`);
};

beforeEach(() => {
    vi.stubGlobal(
        'fetch',
        vi.fn(async (url) => {
            const path = String(url).replace(/^.*\/api\/v1/, '');
            if (path === '/users/me') return jsonResponse(ana);
            if (path === '/admin/users') return jsonResponse([ana]);
            const catalogPath = path.replace(/\?.*$/, '');
            return jsonResponse(catalogFor(catalogPath));
        }),
    );
});

describe('Sin sesión', () => {
    beforeEach(() => useAuth.mockReturnValue(sessionFor()));

    it.each([
        [ROUTES.HOME, HOME],
        [ROUTES.LOGIN, LOGIN],
        [ROUTES.REGISTER, 'Crea tu cuenta'],
        [ROUTES.UNAUTHORIZED, 'No tienes acceso a esta sección'],
        ['/no-existe', 'Esta página no existe'],
    ])('%s muestra su página', async (route, title) => {
        visit(route);
        await expectPage(title, route);
    });

    it.each([
        ROUTES.PROFILE,
        ROUTES.SECURITY,
        ROUTES.USERS,
        ROUTES.CATALOGS,
        ROUTES.EDITIONS,
        ROUTES.MY_PROJECTS,
        ROUTES.PROJECTS,
        ROUTES.ONBOARDING,
    ])('%s lleva al inicio de sesión', async (route) => {
        visit(route);
        await expectPage(LOGIN, ROUTES.LOGIN);
    });
});

describe('Estudiante al día', () => {
    beforeEach(() => useAuth.mockReturnValue(sessionFor({ role: ROLES.STUDENT })));

    it.each([
        [ROUTES.PROFILE, 'Mi perfil'],
        [ROUTES.SECURITY, 'Seguridad'],
        [ROUTES.MY_PROJECTS, 'Mis proyectos'],
    ])('%s muestra su página', async (route, title) => {
        visit(route);
        await expectPage(title, route);
    });

    it.each([ROUTES.USERS, ROUTES.CATALOGS, ROUTES.EDITIONS, ROUTES.PROJECTS])('%s no está permitido', async (route) => {
        visit(route);
        await expectPage('No tienes acceso a esta sección', ROUTES.UNAUTHORIZED);
    });

    it.each([ROUTES.LOGIN, ROUTES.REGISTER, ROUTES.ONBOARDING])('%s lleva al inicio', async (route) => {
        visit(route);
        await expectPage(HOME, ROUTES.HOME);
    });
});

describe('Gestión', () => {
    beforeEach(() => useAuth.mockReturnValue(sessionFor({ role: ROLES.MACONDOLAB })));

    it.each([
        [ROUTES.USERS, 'Usuarios'],
        [ROUTES.CATALOGS, 'Catálogos'],
        [ROUTES.EDITIONS, 'Ediciones'],
        [ROUTES.PROJECTS, 'Proyectos'],
    ])('%s muestra su página', async (route, title) => {
        visit(route);
        await expectPage(title, route);
    });

    it('Mis proyectos es de los estudiantes', async () => {
        visit(ROUTES.MY_PROJECTS);
        await expectPage('No tienes acceso a esta sección', ROUTES.UNAUTHORIZED);
    });

    it('al iniciar sesión llega a Usuarios', async () => {
        visit(ROUTES.LOGIN);
        await expectPage('Usuarios', ROUTES.USERS);
    });
});

describe('Primer ingreso pendiente', () => {
    beforeEach(() =>
        useAuth.mockReturnValue(sessionFor({ role: ROLES.TEACHER, pendingSteps: ['CHANGE_PASSWORD', 'DATA_CONSENT'] })),
    );

    it.each([ROUTES.PROFILE, ROUTES.SECURITY, ROUTES.USERS])('%s lleva al primer ingreso', async (route) => {
        visit(route);
        await expectPage('Crea tu contraseña', ROUTES.ONBOARDING);
    });

    it('el inicio público sigue disponible', async () => {
        visit(ROUTES.HOME);
        await expectPage(HOME, ROUTES.HOME);
    });
});
