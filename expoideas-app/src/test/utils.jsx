import { vi } from 'vitest';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ROLES, isManagement } from '@/lib/roles';
import { fullName } from '@/lib/text';

/** Caché de TanStack Query aislada por prueba y sin reintentos. */
export const createTestQueryClient = () =>
    new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

/**
 * Renderiza con caché de datos y router en memoria.
 *
 * @param {string} [options.route] URL inicial
 */
export function renderWithProviders(ui, { route = '/', queryClient = createTestQueryClient() } = {}) {
    return render(
        <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
        </QueryClientProvider>,
    );
}

/**
 * Valor de useAuth para una sesión con ese rol (o sin sesión si no hay rol). Cada
 * archivo de prueba simula el hook con vi.mock('@/features/auth/useAuth').
 */
export const sessionFor = ({ role = null, pendingSteps = [], user = {} } = {}) => {
    const profile = { id: 1, email: 'marta@empresa.com', firstName: 'Marta', lastName: 'Ríos', photoId: null, ...user };
    return {
        token: role ? 'token' : null,
        role,
        roleReady: true,
        pendingSteps,
        user: role ? { ...profile, fullName: fullName(profile.firstName, profile.lastName) } : null,
        isAdmin: role === ROLES.ADMIN,
        isManagement: isManagement(role),
        login: vi.fn(),
        logout: vi.fn(),
        updateUser: vi.fn(),
        completeStep: vi.fn(),
    };
};

/**
 * JWT sin firma verificable, suficiente para jwtDecode: rol y vencimiento en una
 * hora (o en `expiresIn` segundos; negativo, ya vencido).
 */
export const fakeJwt = (role, subject = 'marta@empresa.com', expiresIn = 3600) => {
    const encode = (object) => btoa(JSON.stringify(object)).replace(/=+$/, '');
    return `${encode({ alg: 'HS256' })}.${encode({ sub: subject, role, exp: Date.now() / 1000 + expiresIn })}.firma`;
};

/** Respuesta de fetch con JSON. */
export const jsonResponse = (body, status = 200, contentType = 'application/json') => ({
    ok: status < 400,
    status,
    headers: { get: () => contentType },
    json: async () => body,
});

/** Error como los que lanza apiClient. */
export const apiError = (message, status, body) => Object.assign(new Error(message), { status, body });
