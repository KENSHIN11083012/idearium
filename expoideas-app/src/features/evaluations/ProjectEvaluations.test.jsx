import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { project } from '@/test/fixtures';
import { apiError, renderWithProviders } from '@/test/utils';
import { ProjectEvaluations } from './ProjectEvaluations';
import { useProjectEvaluations, useProjectRubric } from './queries';

vi.mock('./queries', () => ({ useProjectEvaluations: vi.fn(), useProjectRubric: vi.fn() }));

const rubric = {
    id: 1,
    track: 'INNPRENDE_I',
    criteria: [
        {
            id: 1,
            position: 1,
            name: 'Planteamiento del problema/necesidad',
            shortName: 'Planteamiento del problema',
            levels: [
                { id: 12, label: 'Deficiente', score: 1.5 },
                { id: 14, label: 'Bueno', score: 4.5 },
            ],
        },
        {
            id: 2,
            position: 2,
            name: 'Objetivos del proyecto',
            shortName: 'Objetivos',
            levels: [{ id: 24, label: 'Bueno', score: 4.5 }],
        },
    ],
};

const marta = {
    id: 4,
    projectId: 10,
    jurorId: 8,
    juror: 'Marta Ríos',
    absent: false,
    grade: 3,
    scale: 'ACCEPTABLE',
    scores: [
        { criterionId: 1, levelId: 12, score: 1.5, comment: 'El problema quedó genérico.' },
        { criterionId: 2, levelId: 24, score: 4.5, comment: null },
    ],
};

const pedro = { id: 5, projectId: 10, jurorId: 9, juror: 'Pedro Ruiz', absent: true, grade: 0, scale: 'FAILING', scores: [] };

const query = (data, extra = {}) => ({ data, isPending: false, error: null, refetch: vi.fn(), ...extra });

const renderPanel = (results) => {
    useProjectEvaluations.mockReturnValue(query(results));
    renderWithProviders(<ProjectEvaluations project={project} />);
};

beforeEach(() => {
    useProjectRubric.mockReturnValue(query(rubric));
});

describe('La evaluación de un proyecto', () => {
    it('muestra la nota, cuántos jurados van, quién falta y lo que puso cada uno', () => {
        renderPanel({
            projectId: 10,
            grade: 2.3,
            scale: 'FAILING',
            jurors: 3,
            evaluations: [marta, pedro],
            pending: [{ userId: 11, fullName: 'Lucía Torres' }],
        });

        // Los criterios se leen de la rúbrica del proyecto, que es la de su edición.
        expect(useProjectRubric).toHaveBeenCalledWith(10);
        expect(screen.getByText('2.3')).toBeInTheDocument();
        expect(screen.getByText('Deficiente')).toBeInTheDocument();
        expect(screen.getByText('2 de 3 jurados calificaron')).toBeInTheDocument();
        expect(screen.getByText('Falta: Lucía Torres.')).toBeInTheDocument();

        const list = within(screen.getByRole('list', { name: 'Evaluaciones de los jurados' }));
        expect(list.getByText('Marta Ríos')).toBeInTheDocument();
        expect(list.getByText('Aceptable · 3.0')).toBeInTheDocument();
        expect(list.getByText('Planteamiento del problema')).toBeInTheDocument();
        expect(list.getByText('· Deficiente')).toBeInTheDocument();
        expect(list.getByText('«El problema quedó genérico.»')).toBeInTheDocument();
        expect(list.getByText('No asistió · 0.0')).toBeInTheDocument();
    });

    it('sin evaluaciones lo dice', () => {
        renderPanel({ projectId: 10, grade: null, scale: null, jurors: 2, evaluations: [], pending: [] });

        expect(screen.getByText('Todavía ningún jurado ha calificado.')).toBeInTheDocument();
        expect(screen.getByText('0 de 2 jurados calificaron')).toBeInTheDocument();
        expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('si la API falla ofrece reintentar', () => {
        useProjectEvaluations.mockReturnValue(query(undefined, { error: apiError('Error del servidor', 500) }));
        renderWithProviders(<ProjectEvaluations project={project} />);

        expect(screen.getByText('No pudimos cargar la evaluación')).toBeInTheDocument();
    });
});
