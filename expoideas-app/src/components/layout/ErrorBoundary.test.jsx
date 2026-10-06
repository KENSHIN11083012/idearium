import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorBoundary } from './ErrorBoundary';

function Broken({ message }) {
    throw new Error(message);
}

const STALE_CHUNK = 'Failed to fetch dynamically imported module: https://idearium.test/expoideas/assets/ProjectPage-a1b2.js';

let reload;

beforeEach(() => {
    reload = vi.fn();
    // React escribe en la consola cada error que atrapa un límite: aquí es lo esperado.
    vi.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
});

describe('Cuando una página falla al pintarse', () => {
    it('se muestra una explicación con la forma de seguir, no una pantalla en blanco', async () => {
        render(
            <ErrorBoundary reload={reload}>
                <Broken message="Cannot read properties of undefined (reading 'members')" />
            </ErrorBoundary>,
        );

        expect(screen.getByRole('heading', { name: 'Algo salió mal' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Ir al inicio/ })).toBeInTheDocument();
        expect(reload).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('button', { name: /Recargar la página/ }));
        expect(reload).toHaveBeenCalledTimes(1);
    });

    it('al ir a otra página el aviso se quita', () => {
        const { rerender } = render(
            <ErrorBoundary resetKey="/proyectos/10">
                <Broken message="falló" />
            </ErrorBoundary>,
        );
        expect(screen.getByRole('heading', { name: 'Algo salió mal' })).toBeInTheDocument();

        rerender(
            <ErrorBoundary resetKey="/perfil">
                <p>Mi perfil</p>
            </ErrorBoundary>,
        );

        expect(screen.getByText('Mi perfil')).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Algo salió mal' })).not.toBeInTheDocument();
    });

    it('lo que no falla se pinta igual', () => {
        render(
            <ErrorBoundary reload={reload}>
                <p>Contenido de la página</p>
            </ErrorBoundary>,
        );

        expect(screen.getByText('Contenido de la página')).toBeInTheDocument();
    });
});

describe('Cuando falta un archivo de la versión anterior (hubo un despliegue)', () => {
    it('la página se recarga sola una vez para traer la versión nueva', () => {
        render(
            <ErrorBoundary reload={reload}>
                <Broken message={STALE_CHUNK} />
            </ErrorBoundary>,
        );

        expect(reload).toHaveBeenCalledTimes(1);
    });

    it('si recargar no lo arregló, no entra en un bucle: lo explica y deja el botón', () => {
        const { unmount } = render(
            <ErrorBoundary reload={reload}>
                <Broken message={STALE_CHUNK} />
            </ErrorBoundary>,
        );
        unmount();

        render(
            <ErrorBoundary reload={reload}>
                <Broken message={STALE_CHUNK} />
            </ErrorBoundary>,
        );

        expect(reload).toHaveBeenCalledTimes(1);
        expect(screen.getByRole('heading', { name: 'Hay una versión nueva de Idearium' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Recargar la página/ })).toBeInTheDocument();
    });
});
