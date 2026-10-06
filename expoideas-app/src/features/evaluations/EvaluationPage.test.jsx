import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, renderWithProviders, sessionFor } from '@/test/utils';
import { project } from '@/test/fixtures';
import { useAuth } from '@/features/auth/useAuth';
import { useProject } from '@/features/projects/queries';
import EvaluationPage from './EvaluationPage';
import { useMyEvaluation, useProjectRubric, useSaveEvaluation } from './queries';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/projects/queries', () => ({ useProject: vi.fn() }));
vi.mock('./queries', () => ({ useProjectRubric: vi.fn(), useMyEvaluation: vi.fn(), useSaveEvaluation: vi.fn() }));

const navigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
    ...(await importOriginal()),
    useParams: () => ({ id: '10' }),
    useNavigate: () => navigate,
}));

const level = (id, label, score) => ({ id, position: id % 10, label, score, description: `Descripción del nivel ${id}` });

/** Dos criterios, como en el póster: el mismo nivel no vale lo mismo en los dos. */
const rubric = {
    id: 1,
    track: 'INNPRENDE_I',
    name: 'Póster de proyecto de innovación',
    criteria: [
        {
            id: 1,
            position: 1,
            name: 'Planteamiento del problema/necesidad',
            shortName: 'Planteamiento del problema',
            levels: [
                level(11, 'Insuficiente', 0),
                level(12, 'Deficiente', 1.5),
                level(13, 'Aceptable', 3.5),
                level(14, 'Bueno', 4.5),
            ],
        },
        {
            id: 2,
            position: 2,
            name: 'Objetivos del proyecto',
            shortName: 'Objetivos',
            levels: [
                level(21, 'Insuficiente', 0),
                level(22, 'Deficiente', 3),
                level(23, 'Aceptable', 4),
                level(24, 'Bueno', 4.5),
            ],
        },
    ],
};

const saved = {
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
    updatedAt: '2026-11-20T10:00:00',
};

const query = (data, extra = {}) => ({ data, isPending: false, error: null, refetch: vi.fn(), ...extra });

let save;

/** @param {number} [options.jurorId] quién califica: el borrador es de cada jurado */
const renderPage = ({ evaluation = null, jurorId = 8 } = {}) => {
    useAuth.mockReturnValue(sessionFor({ role: 'JUDGE', user: { id: jurorId } }));
    useMyEvaluation.mockReturnValue(query(evaluation));
    return renderWithProviders(<EvaluationPage />);
};

/** Donde queda en el navegador el borrador del jurado 8 para el proyecto 10. */
const DRAFT_KEY = 'expoideas:evaluation-draft:8:10';

const criterion = (name) => within(screen.getByRole('radiogroup', { name: new RegExp(name) }));
const choose = (name, levelName) => userEvent.click(criterion(name).getByRole('radio', { name: levelName }));
const saveButton = () => screen.getByRole('button', { name: /Guardar evaluación/ });

beforeEach(() => {
    navigate.mockClear();
    save = { mutateAsync: vi.fn().mockResolvedValue(saved), isPending: false };
    useProject.mockReturnValue(query(project));
    useProjectRubric.mockReturnValue(query(rubric));
    useSaveEvaluation.mockReturnValue(save);
});

afterEach(() => localStorage.clear());

describe('El tablero del jurado', () => {
    it('muestra la rúbrica del proyecto, la de su edición: cada criterio con sus niveles, su valor y su descripción', () => {
        renderPage();

        expect(useProjectRubric).toHaveBeenCalledWith('10');
        expect(screen.getByRole('heading', { level: 1, name: 'BioSensor' })).toBeInTheDocument();
        expect(screen.getByText(/Póster de proyecto de innovación/)).toBeInTheDocument();
        expect(criterion('Planteamiento').getAllByRole('radio')).toHaveLength(4);
        // El mismo nivel vale distinto según el criterio.
        expect(criterion('Planteamiento').getByRole('radio', { name: 'Deficiente, 1.5' })).not.toBeChecked();
        expect(criterion('Objetivos').getByRole('radio', { name: 'Deficiente, 3.0' })).toBeInTheDocument();
        expect(screen.getByText('Descripción del nivel 13')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Ver proyecto y entregables/ })).toHaveAttribute('href', '/proyectos/10');
    });

    it('la nota aparece en vivo cuando todos los criterios tienen nivel', async () => {
        renderPage();

        expect(screen.getByText('0 de 2 criterios')).toBeInTheDocument();
        await choose('Planteamiento', 'Bueno, 4.5');
        expect(screen.getByText('1 de 2 criterios')).toBeInTheDocument();
        expect(screen.queryByText(/Nota:/)).not.toBeInTheDocument();

        await choose('Objetivos', 'Aceptable, 4.0');
        // (4.5 + 4.0) / 2 = 4.25, que sube a 4.3.
        expect(screen.getByText('2 de 2 criterios')).toBeInTheDocument();
        expect(screen.getByText('4.3')).toBeInTheDocument();
        expect(screen.getByText(/^Nota:.*· Bueno$/)).toBeInTheDocument();
    });

    it('guarda un nivel por criterio y vuelve a la lista', async () => {
        renderPage();

        await choose('Planteamiento', 'Bueno, 4.5');
        await choose('Objetivos', 'Aceptable, 4.0');
        await userEvent.type(screen.getByLabelText(/Observación de «Objetivos»/), '  Claros y medibles.  ');
        await userEvent.click(saveButton());

        await waitFor(() =>
            expect(save.mutateAsync).toHaveBeenCalledWith({
                absent: false,
                scores: [
                    { criterionId: 1, levelId: 14, comment: null },
                    { criterionId: 2, levelId: 23, comment: 'Claros y medibles.' },
                ],
            }),
        );
        expect(navigate).toHaveBeenCalledWith('/jurado/proyectos');
    });

    it('no deja guardar con un criterio sin nivel', async () => {
        renderPage();

        await choose('Planteamiento', 'Bueno, 4.5');
        await userEvent.click(saveButton());

        expect(await screen.findByText('Elige un nivel para este criterio')).toBeInTheDocument();
        expect(save.mutateAsync).not.toHaveBeenCalled();
    });

    it('el criterio que falta se anuncia: el foco va a él y su error queda asociado al grupo', async () => {
        renderPage();

        await choose('Planteamiento', 'Bueno, 4.5');
        await userEvent.click(saveButton());

        const missing = await screen.findByRole('radiogroup', { name: /Objetivos/ });
        expect(missing).toBeInvalid();
        expect(missing).toHaveAccessibleDescription('Elige un nivel para este criterio');
        // Quien usa lector de pantalla cae en el criterio que hay que corregir, no en el botón de guardar.
        expect(within(missing).getAllByRole('radio')[0]).toHaveFocus();

        const done = screen.getByRole('radiogroup', { name: /Planteamiento/ });
        expect(done).not.toBeInvalid();
        expect(done).toBeRequired();
    });

    it('por debajo de 3.0 la observación es obligatoria; desde 3.0, opcional', async () => {
        renderPage();

        await choose('Planteamiento', 'Deficiente, 1.5');
        await choose('Objetivos', 'Deficiente, 3.0');
        expect(screen.getByLabelText(/Observación de «Planteamiento del problema»/)).toBeRequired();
        expect(screen.getByLabelText(/Observación de «Objetivos»/)).not.toBeRequired();

        await userEvent.click(saveButton());
        expect(await screen.findByText(/la observación es obligatoria por debajo de 3.0/)).toBeInTheDocument();
        expect(save.mutateAsync).not.toHaveBeenCalled();

        await userEvent.type(screen.getByLabelText(/Observación de «Planteamiento del problema»/), 'Muy genérico.');
        await userEvent.click(saveButton());
        await waitFor(() => expect(save.mutateAsync).toHaveBeenCalledTimes(1));
        expect(save.mutateAsync.mock.calls[0][0].scores[0]).toEqual({ criterionId: 1, levelId: 12, comment: 'Muy genérico.' });
    });

    it('lo que rechaza la API se pinta en su criterio', async () => {
        save.mutateAsync.mockRejectedValue(
            apiError('Los datos enviados no son válidos.', 400, { fields: { 'scores.2.comment': 'Explica la calificación' } }),
        );
        renderPage();

        await choose('Planteamiento', 'Bueno, 4.5');
        await choose('Objetivos', 'Aceptable, 4.0');
        await userEvent.click(saveButton());

        expect(await screen.findByText('Explica la calificación')).toBeInTheDocument();
        expect(screen.getByLabelText(/Observación de «Objetivos»/)).toBeInvalid();
        expect(navigate).not.toHaveBeenCalled();
    });
});

describe('Corregir y marcar que no asistieron', () => {
    it('quien ya calificó ve lo que puso y puede corregirlo', async () => {
        renderPage({ evaluation: saved });

        expect(screen.getByText(/Ya calificaste este proyecto: 3.0 · Aceptable/)).toBeInTheDocument();
        expect(criterion('Planteamiento').getByRole('radio', { name: 'Deficiente, 1.5' })).toBeChecked();
        expect(criterion('Objetivos').getByRole('radio', { name: 'Bueno, 4.5' })).toBeChecked();
        expect(screen.getByLabelText(/Observación de «Planteamiento del problema»/)).toHaveValue('El problema quedó genérico.');

        await choose('Planteamiento', 'Aceptable, 3.5');
        await userEvent.click(saveButton());

        await waitFor(() => expect(save.mutateAsync).toHaveBeenCalledTimes(1));
        expect(save.mutateAsync.mock.calls[0][0].scores[0]).toEqual({
            criterionId: 1,
            levelId: 13,
            comment: 'El problema quedó genérico.',
        });
    });

    it('«No asistió» pide confirmación y guarda la evaluación en cero', async () => {
        renderPage();

        await userEvent.click(screen.getByRole('button', { name: 'No asistió' }));
        const dialog = within(screen.getByRole('alertdialog'));
        expect(dialog.getByText(/quedará en 0.0/)).toBeInTheDocument();
        expect(save.mutateAsync).not.toHaveBeenCalled();

        await userEvent.click(dialog.getByRole('button', { name: 'Sí, no asistió' }));

        await waitFor(() => expect(save.mutateAsync).toHaveBeenCalledWith({ absent: true }));
        expect(navigate).toHaveBeenCalledWith('/jurado/proyectos');
    });

    it('si ya había marcado que no asistieron, lo dice y deja calificar', () => {
        renderPage({ evaluation: { ...saved, absent: true, grade: 0, scale: 'FAILING', scores: [] } });

        expect(screen.getByText('Marcaste que el equipo no asistió')).toBeInTheDocument();
        expect(screen.getByText('0 de 2 criterios')).toBeInTheDocument();
    });

    it('a quien no es jurado del proyecto se le explica', () => {
        useAuth.mockReturnValue(sessionFor({ role: 'JUDGE', user: { id: 8 } }));
        useMyEvaluation.mockReturnValue(
            query(undefined, { error: apiError('Solo los jurados asignados califican este proyecto', 403) }),
        );
        renderWithProviders(<EvaluationPage />);

        expect(screen.getByText('No puedes calificar este proyecto')).toBeInTheDocument();
        expect(screen.getByText('Solo los jurados asignados califican este proyecto')).toBeInTheDocument();
        expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
    });
});

describe('Lo que el jurado lleva sin guardar', () => {
    const NOTICE = 'Recuperamos lo que dejaste sin guardar';

    /** Marca un criterio y escribe una observación, y cierra la página sin guardar. */
    const leaveHalfway = async (options) => {
        const page = renderPage(options);
        await choose('Planteamiento', 'Bueno, 4.5');
        await userEvent.type(screen.getByLabelText(/Observación de «Planteamiento del problema»/), 'Bien delimitado.');
        page.unmount();
    };

    it('sigue ahí al volver a abrir la página, con un aviso de que falta guardarlo', async () => {
        await leaveHalfway();

        renderPage();

        expect(screen.getByText(NOTICE)).toBeInTheDocument();
        expect(criterion('Planteamiento').getByRole('radio', { name: 'Bueno, 4.5' })).toBeChecked();
        expect(screen.getByLabelText(/Observación de «Planteamiento del problema»/)).toHaveValue('Bien delimitado.');
        expect(screen.getByText('1 de 2 criterios')).toBeInTheDocument();
        expect(save.mutateAsync).not.toHaveBeenCalled();
    });

    it('se puede descartar para volver a lo guardado', async () => {
        await leaveHalfway({ evaluation: saved });
        renderPage({ evaluation: saved });
        expect(criterion('Planteamiento').getByRole('radio', { name: 'Bueno, 4.5' })).toBeChecked();

        await userEvent.click(screen.getByRole('button', { name: /Descartar el borrador/ }));

        expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
        expect(criterion('Planteamiento').getByRole('radio', { name: 'Deficiente, 1.5' })).toBeChecked();
        expect(screen.getByLabelText(/Observación de «Planteamiento del problema»/)).toHaveValue('El problema quedó genérico.');
        expect(localStorage.getItem(DRAFT_KEY)).toBeNull();
    });

    it('al guardar la evaluación, el borrador se borra', async () => {
        await leaveHalfway();
        renderPage();
        expect(localStorage.getItem(DRAFT_KEY)).not.toBeNull();

        await choose('Objetivos', 'Aceptable, 4.0');
        await userEvent.click(saveButton());

        await waitFor(() => expect(navigate).toHaveBeenCalledWith('/jurado/proyectos'));
        expect(save.mutateAsync.mock.calls[0][0].scores[0]).toEqual({ criterionId: 1, levelId: 14, comment: 'Bien delimitado.' });
        expect(localStorage.getItem(DRAFT_KEY)).toBeNull();
    });

    it('si guardar falla, el borrador se queda', async () => {
        save.mutateAsync.mockRejectedValue(apiError('Tu sesión expiró. Inicia sesión nuevamente.', 401));
        renderPage();

        await choose('Planteamiento', 'Bueno, 4.5');
        await choose('Objetivos', 'Aceptable, 4.0');
        await userEvent.click(saveButton());

        expect(await screen.findByText('Tu sesión expiró. Inicia sesión nuevamente.')).toBeInTheDocument();
        expect(JSON.parse(localStorage.getItem(DRAFT_KEY)).scores).toEqual({
            c1: { levelId: 14, comment: '' },
            c2: { levelId: 23, comment: '' },
        });
    });

    it('deshacer a mano lo marcado no deja un borrador igual a lo guardado', async () => {
        renderPage({ evaluation: saved });

        await choose('Planteamiento', 'Bueno, 4.5');
        expect(localStorage.getItem(DRAFT_KEY)).not.toBeNull();

        await choose('Planteamiento', 'Deficiente, 1.5');
        expect(localStorage.getItem(DRAFT_KEY)).toBeNull();
    });

    it('es de cada jurado: otro que califique en el mismo navegador no lo ve', async () => {
        await leaveHalfway();

        renderPage({ jurorId: 9 });

        expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
        expect(screen.getByText('0 de 2 criterios')).toBeInTheDocument();
    });

    it('si la evaluación guardada cambió después (calificó desde otro equipo), el borrador ya no aplica', async () => {
        await leaveHalfway();

        renderPage({ evaluation: saved });

        expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
        expect(criterion('Planteamiento').getByRole('radio', { name: 'Deficiente, 1.5' })).toBeChecked();
        expect(localStorage.getItem(DRAFT_KEY)).toBeNull();
    });

    it('un borrador que no encaja con la rúbrica de hoy se descarta', () => {
        localStorage.setItem(
            DRAFT_KEY,
            JSON.stringify({ base: null, savedAt: Date.now(), scores: { c1: { levelId: 999, comment: '' } } }),
        );

        renderPage();

        expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
        expect(screen.getByText('0 de 2 criterios')).toBeInTheDocument();
    });
});

describe('Cuando falla volver a consultar con el tablero en pantalla', () => {
    it('un fallo pasajero (se fue la conexión) no quita el tablero', () => {
        useProject.mockReturnValue(query(project, { error: apiError('Error de red. Verifica tu conexión.', 0) }));
        renderPage({ evaluation: saved });

        expect(criterion('Planteamiento').getByRole('radio', { name: 'Deficiente, 1.5' })).toBeChecked();
        expect(screen.queryByText('No puedes calificar este proyecto')).not.toBeInTheDocument();
    });

    it('si la API responde que ya no es jurado, sí', () => {
        useAuth.mockReturnValue(sessionFor({ role: 'JUDGE', user: { id: 8 } }));
        useMyEvaluation.mockReturnValue(
            query(saved, { error: apiError('Solo los jurados asignados califican este proyecto', 403) }),
        );
        renderWithProviders(<EvaluationPage />);

        expect(screen.getByText('No puedes calificar este proyecto')).toBeInTheDocument();
        expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
    });
});
