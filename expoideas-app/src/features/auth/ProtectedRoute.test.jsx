import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ROUTES } from '@/lib/routes';
import { sessionFor } from '@/test/utils';
import { ProtectedRoute } from './ProtectedRoute';
import { useAuth } from './useAuth';

vi.mock('./useAuth', () => ({ useAuth: vi.fn() }));

function renderProjects(session) {
    useAuth.mockReturnValue(session);
    render(
        <MemoryRouter initialEntries={[ROUTES.PROJECTS]}>
            <Routes>
                <Route
                    path={ROUTES.PROJECTS}
                    element={
                        <ProtectedRoute allowedRoles={['TEACHER', 'MACONDOLAB', 'ADMIN']}>
                            <p>Listado de proyectos</p>
                        </ProtectedRoute>
                    }
                />
                <Route path={ROUTES.LOGIN} element={<p>Inicia sesión</p>} />
                <Route path={ROUTES.ONBOARDING} element={<p>Primer ingreso</p>} />
                <Route path={ROUTES.UNAUTHORIZED} element={<p>No autorizado</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('Rutas por rol', () => {
    it('sin sesión lleva a iniciar sesión', () => {
        renderProjects(sessionFor());
        expect(screen.getByText('Inicia sesión')).toBeInTheDocument();
    });

    it('con pasos de primer ingreso lleva a resolverlos', () => {
        renderProjects(sessionFor({ role: 'TEACHER', pendingSteps: ['COMPLETE_PROFILE'] }));
        expect(screen.getByText('Primer ingreso')).toBeInTheDocument();
    });

    it('con el rol permitido muestra la página', () => {
        renderProjects(sessionFor({ role: 'TEACHER' }));
        expect(screen.getByText('Listado de proyectos')).toBeInTheDocument();
    });

    it('niega el acceso solo cuando el rol ya es el de la base de datos', () => {
        // El token dice estudiante, pero /users/me todavía no respondió: puede que la gestión lo haya cambiado.
        renderProjects({ ...sessionFor({ role: 'STUDENT' }), roleReady: false });
        expect(screen.queryByText('No autorizado')).not.toBeInTheDocument();
        expect(screen.queryByText('Listado de proyectos')).not.toBeInTheDocument();
    });

    it('con el rol confirmado y sin permiso lleva a no autorizado', () => {
        renderProjects(sessionFor({ role: 'STUDENT' }));
        expect(screen.getByText('No autorizado')).toBeInTheDocument();
    });
});
