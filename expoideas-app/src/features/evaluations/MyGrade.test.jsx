import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { project } from '@/test/fixtures';
import { renderWithProviders } from '@/test/utils';
import { MyGrade } from './MyGrade';
import { useMyGrade } from './queries';

vi.mock('./queries', () => ({ useMyGrade: vi.fn() }));

const published = {
    projectId: 10,
    grade: 4.3,
    scale: 'GOOD',
    evaluated: 2,
    publishedAt: '2026-11-25T08:00:00',
    criteria: [
        {
            criterionId: 1,
            position: 2,
            name: 'Planteamiento del problema',
            comments: ['El problema quedó genérico.', 'Falta la población afectada.'],
        },
    ],
};

describe('La nota publicada del equipo', () => {
    it('muestra la nota final, el nivel y las observaciones sin nombres', () => {
        useMyGrade.mockReturnValue({ data: published });
        renderWithProviders(<MyGrade project={project} />);

        expect(screen.getByRole('heading', { name: 'Tu evaluación' })).toBeInTheDocument();
        expect(screen.getByText('4.3')).toBeInTheDocument();
        expect(screen.getByText('Bueno')).toBeInTheDocument();
        expect(screen.getByText(/Calificaron 2 jurados/)).toBeInTheDocument();
        expect(screen.getByText('2. Planteamiento del problema')).toBeInTheDocument();
        expect(screen.getByText('«El problema quedó genérico.»')).toBeInTheDocument();
        expect(screen.getByText('«Falta la población afectada.»')).toBeInTheDocument();
        expect(screen.queryByText(/Marta|Pedro/)).not.toBeInTheDocument();
    });

    it('sin observaciones lo dice', () => {
        useMyGrade.mockReturnValue({ data: { ...published, evaluated: 1, criteria: [] } });
        renderWithProviders(<MyGrade project={project} />);

        expect(screen.getByText(/Calificó 1 jurado/)).toBeInTheDocument();
        expect(screen.getByText('Los jurados no dejaron observaciones.')).toBeInTheDocument();
    });

    it('mientras no esté publicada no aparece nada', () => {
        useMyGrade.mockReturnValue({ data: null });
        renderWithProviders(<MyGrade project={project} />);

        expect(screen.queryByText('Tu evaluación')).not.toBeInTheDocument();
    });
});
