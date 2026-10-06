import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import CatalogsPage from './CatalogsPage';
import { useAuth } from '@/features/auth/useAuth';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
// El panel carga datos de la API; aquí solo importa qué pestañas se ofrecen.
vi.mock('./CatalogPanel', () => ({
    CatalogPanel: ({ catalog }) => <p>Panel de {catalog.label}</p>,
}));

const tabs = () => screen.getAllByRole('tab').map((tab) => tab.textContent);

describe('Catálogos por rol', () => {
    it('el administrador gestiona la estructura institucional y la clasificación', () => {
        useAuth.mockReturnValue({ isAdmin: true });
        render(<CatalogsPage />);

        expect(tabs()).toEqual([
            'Sedes',
            'Facultades',
            'Programas académicos',
            'Sectores',
            'Tipos de prototipo',
            'Palabras clave',
        ]);
        expect(screen.getByText('Panel de Sedes')).toBeInTheDocument();
    });

    it('MacondoLab solo ve la clasificación de proyectos', () => {
        useAuth.mockReturnValue({ isAdmin: false });
        render(<CatalogsPage />);

        expect(tabs()).toEqual(['Sectores', 'Tipos de prototipo', 'Palabras clave']);
        expect(screen.getByText('Panel de Sectores')).toBeInTheDocument();
        expect(screen.queryByText(/Sedes/)).not.toBeInTheDocument();
    });
});
