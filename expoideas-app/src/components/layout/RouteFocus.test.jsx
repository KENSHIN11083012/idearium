import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { RouteFocus } from './RouteFocus';

const renderApp = () =>
    render(
        <MemoryRouter initialEntries={['/']}>
            <RouteFocus />
            <nav>
                <Link to="/perfil">Mi perfil</Link>
            </nav>
            <main>
                <Routes>
                    <Route path="/" element={<h1>Inicio</h1>} />
                    <Route path="/perfil" element={<h1>Mi perfil</h1>} />
                </Routes>
            </main>
        </MemoryRouter>,
    );

describe('El foco al cambiar de página', () => {
    it('va al contenido principal, no se queda en el enlace del menú', async () => {
        renderApp();

        await userEvent.click(screen.getByRole('link', { name: 'Mi perfil' }));

        expect(screen.getByRole('heading', { name: 'Mi perfil' })).toBeInTheDocument();
        expect(screen.getByRole('main')).toHaveFocus();
        // Recibe el foco por código, pero no entra en el recorrido del tabulador.
        expect(screen.getByRole('main')).toHaveAttribute('tabindex', '-1');
    });

    it('al cargar la app no se lleva el foco de donde esté', () => {
        renderApp();

        expect(screen.getByRole('main')).not.toHaveFocus();
        expect(screen.getByRole('main')).not.toHaveAttribute('tabindex');
    });
});
