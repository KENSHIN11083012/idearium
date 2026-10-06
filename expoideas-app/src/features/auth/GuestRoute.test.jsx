import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ROUTES } from '@/lib/routes';
import { sessionFor } from '@/test/utils';
import { GuestRoute } from './GuestRoute';
import { useAuth } from './useAuth';

vi.mock('./useAuth', () => ({ useAuth: vi.fn() }));

const EVALUATION = ROUTES.evaluate(10);

function Evaluation() {
    return <p>Tablero del jurado {useLocation().search}</p>;
}

/** El inicio de sesión, al que se llegó (o no) desde una página que pedía sesión. */
function renderLogin(auth, from) {
    useAuth.mockReturnValue(auth);
    render(
        <MemoryRouter initialEntries={[{ pathname: ROUTES.LOGIN, state: from ? { from } : null }]}>
            <Routes>
                <Route
                    path={ROUTES.LOGIN}
                    element={
                        <GuestRoute>
                            <p>Formulario de inicio de sesión</p>
                        </GuestRoute>
                    }
                />
                <Route path={EVALUATION} element={<Evaluation />} />
                <Route path={ROUTES.ONBOARDING} element={<p>Primer ingreso</p>} />
                <Route path={ROUTES.HOME} element={<p>Inicio</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

describe('Las páginas de quien no ha entrado', () => {
    it('sin sesión se muestran', () => {
        renderLogin(sessionFor());

        expect(screen.getByText('Formulario de inicio de sesión')).toBeInTheDocument();
    });

    it('al entrar, quien venía de una página que pedía sesión vuelve a ella', () => {
        renderLogin(sessionFor({ role: 'JUDGE' }), { pathname: EVALUATION, search: '?desde=correo' });

        expect(screen.getByText('Tablero del jurado ?desde=correo')).toBeInTheDocument();
    });

    it('quien entró por su cuenta va al inicio de su rol', () => {
        renderLogin(sessionFor({ role: 'JUDGE' }));

        expect(screen.getByText('Inicio')).toBeInTheDocument();
    });

    it('con pasos de primer ingreso pendientes, primero se resuelven', () => {
        renderLogin(sessionFor({ role: 'JUDGE', pendingSteps: ['DATA_CONSENT'] }), { pathname: EVALUATION });

        expect(screen.getByText('Primer ingreso')).toBeInTheDocument();
    });
});
