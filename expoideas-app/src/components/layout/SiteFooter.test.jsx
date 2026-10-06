import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, sessionFor } from '@/test/utils';
import { useAuth } from '@/features/auth/useAuth';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

/** La versión se lee al cargar el módulo: cada prueba lo carga de nuevo con la suya. */
const renderFooter = async (version) => {
    vi.resetModules();
    if (version !== undefined) vi.stubEnv('VITE_APP_VERSION', version);
    const { SiteFooter } = await import('./SiteFooter');
    useAuth.mockReturnValue(sessionFor());
    renderWithProviders(<SiteFooter />);
};

afterEach(() => vi.unstubAllEnvs());

describe('La versión en el pie', () => {
    it('es la etiqueta de la imagen publicada', async () => {
        await renderFooter('211162e');

        expect(screen.getByTitle('Versión de la plataforma')).toHaveTextContent('v. 211162e');
    });

    it('fuera de una imagen publicada dice «local»', async () => {
        await renderFooter('');

        expect(screen.getByTitle('Versión de la plataforma')).toHaveTextContent('v. local');
    });
});
