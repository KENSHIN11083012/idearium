import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { project } from '@/test/fixtures';
import { apiError, renderWithProviders } from '@/test/utils';
import { useMyEvaluations } from '@/features/evaluations/queries';
import JuryProjectsPage from './JuryProjectsPage';
import { useJuryProjects } from './queries';

vi.mock('./queries', () => ({ useJuryProjects: vi.fn() }));
vi.mock('@/features/evaluations/queries', () => ({ useMyEvaluations: vi.fn() }));

beforeEach(() => {
    useMyEvaluations.mockReturnValue({ data: [] });
});

const renderPage = (query) => {
    useJuryProjects.mockReturnValue({ data: undefined, isPending: false, error: null, refetch: vi.fn(), ...query });
    renderWithProviders(<JuryProjectsPage />);
};

describe('Proyectos por evaluar', () => {
    it('muestra cada proyecto asignado con su equipo y el enlace a la ficha', () => {
        renderPage({ data: [project] });

        expect(screen.getByRole('heading', { name: 'BioSensor' })).toBeInTheDocument();
        expect(screen.getByText('Carlos Mendoza')).toBeInTheDocument();
        expect(screen.getByText('1 integrante')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Ver proyecto/ })).toHaveAttribute('href', '/proyectos/10');
    });

    it('lo que falta por calificar se ve pendiente, con el botón para hacerlo', () => {
        renderPage({ data: [project] });

        expect(screen.getByText('Pendiente')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Calificar' })).toHaveAttribute('href', '/jurado/proyectos/10/calificar');
    });

    it('lo ya calificado muestra la nota y se puede corregir', () => {
        const other = { ...project, id: 11, title: 'Mercado a la puerta' };
        useMyEvaluations.mockReturnValue({
            data: [
                { projectId: 10, absent: false, grade: 4.4 },
                { projectId: 11, absent: true, grade: 0 },
            ],
        });
        renderPage({ data: [project, other] });

        expect(screen.getByText('Calificado · 4.4')).toBeInTheDocument();
        expect(screen.getByText('No asistió · 0.0')).toBeInTheDocument();
        expect(screen.queryByText('Pendiente')).not.toBeInTheDocument();
        const card = within(screen.getByRole('heading', { name: 'BioSensor' }).closest('[data-slot="card"]'));
        expect(card.getByRole('link', { name: 'Corregir calificación' })).toHaveAttribute(
            'href',
            '/jurado/proyectos/10/calificar',
        );
    });

    it('si no se sabe qué se ha calificado, los proyectos se ven igual', () => {
        useMyEvaluations.mockReturnValue({ data: undefined, error: apiError('Error del servidor', 500) });
        renderPage({ data: [project] });

        expect(screen.getByRole('heading', { name: 'BioSensor' })).toBeInTheDocument();
    });

    it('sin asignaciones lo explica', () => {
        renderPage({ data: [] });

        expect(screen.getByText('Todavía no tienes proyectos asignados')).toBeInTheDocument();
    });

    it('si la API falla ofrece reintentar', () => {
        renderPage({ error: apiError('Error del servidor', 500) });

        expect(screen.getByText('No pudimos cargar tus proyectos')).toBeInTheDocument();
    });
});
