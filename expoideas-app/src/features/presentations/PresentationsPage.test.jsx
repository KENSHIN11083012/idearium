import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEditions } from '@/features/editions/queries';
import { useWholeProjectDirectory } from '@/features/projects/directory';
import { openEdition } from '@/test/fixtures';
import { apiError, renderWithProviders } from '@/test/utils';
import PresentationsPage from './PresentationsPage';
import { useRemindJurors } from '@/features/evaluations/queries';
import { usePresentationAgenda, useSchedulePresentation } from './queries';

vi.mock('@/features/editions/queries', () => ({ useEditions: vi.fn() }));
vi.mock('@/features/projects/directory', () => ({ useWholeProjectDirectory: vi.fn() }));
vi.mock('./queries', () => ({ usePresentationAgenda: vi.fn(), useSchedulePresentation: vi.fn() }));
vi.mock('@/features/evaluations/queries', () => ({ useRemindJurors: vi.fn() }));

const bioSensor = { id: 10, title: 'BioSensor', leader: 'Ana María Pérez', teacher: 'Carlos Mendoza', track: 'INNPRENDE_I' };
const riego = { id: 11, title: 'Riego inteligente', leader: 'Luis Gómez', teacher: 'Carlos Mendoza', track: 'INNPRENDE_I' };

const appointment = {
    id: 3,
    projectId: 10,
    projectTitle: 'BioSensor',
    leader: 'Ana María Pérez',
    teacher: 'Carlos Mendoza',
    startsAt: '2026-11-20T09:30:00',
    place: 'Auditorio Jorge Artel',
    notes: 'Llegar 15 minutos antes.',
};

let schedule;
let remind;

const renderPage = ({ projects = [bioSensor, riego], agenda = [appointment] } = {}) => {
    useEditions.mockReturnValue({ data: [openEdition], isPending: false });
    useWholeProjectDirectory.mockReturnValue({ data: projects, isPending: false, error: null, refetch: vi.fn() });
    usePresentationAgenda.mockReturnValue({ data: agenda, isPending: false, error: null, refetch: vi.fn() });
    renderWithProviders(<PresentationsPage />);
    return within(screen.getByRole('table'));
};

beforeEach(() => {
    schedule = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    useSchedulePresentation.mockReturnValue(schedule);
    remind = { mutateAsync: vi.fn().mockResolvedValue({ jurors: 2, projects: 3 }), isPending: false };
    useRemindJurors.mockReturnValue(remind);
});

describe('La agenda de sustentaciones', () => {
    it('lista cada proyecto de la cátedra con su cita o sin ella, las citas primero', () => {
        const table = renderPage();
        const rows = table.getAllByRole('row').slice(1);

        expect(rows[0]).toHaveTextContent('BioSensor');
        expect(rows[0]).toHaveTextContent(/20 de noviembre de 2026/);
        expect(rows[0]).toHaveTextContent('Auditorio Jorge Artel');
        expect(rows[1]).toHaveTextContent('Riego inteligente');
        expect(rows[1]).toHaveTextContent('Sin programar');
        expect(screen.getByText('2 proyectos · 1 sin programar')).toBeInTheDocument();
        expect(useWholeProjectDirectory).toHaveBeenLastCalledWith({ editionId: '1', track: 'INNPRENDE_I' });
    });

    it('programa una cita con fecha, lugar e indicaciones', async () => {
        const user = userEvent.setup();
        const table = renderPage();

        await user.click(within(table.getAllByRole('row')[2]).getByRole('button', { name: /Programar/ }));
        const dialog = within(await screen.findByRole('dialog'));
        expect(dialog.getByText(/"Riego inteligente"/)).toBeInTheDocument();
        await user.type(dialog.getByLabelText(/Fecha y hora/), '2026-11-21T10:00');
        await user.type(dialog.getByLabelText(/Lugar/), 'Sala 302');
        await user.click(dialog.getByRole('button', { name: 'Programar' }));

        await waitFor(() =>
            expect(schedule.mutateAsync).toHaveBeenCalledWith({
                projectId: 11,
                body: { startsAt: '2026-11-21T10:00', place: 'Sala 302', notes: null },
            }),
        );
    });

    it('sin lugar no programa', async () => {
        const user = userEvent.setup();
        const table = renderPage();

        await user.click(within(table.getAllByRole('row')[2]).getByRole('button', { name: /Programar/ }));
        const dialog = within(await screen.findByRole('dialog'));
        await user.click(dialog.getByRole('button', { name: 'Programar' }));

        expect(await dialog.findByText('Indica el lugar')).toBeInTheDocument();
        expect(schedule.mutateAsync).not.toHaveBeenCalled();
    });

    it('cambia una cita existente con sus datos cargados', async () => {
        const user = userEvent.setup();
        const table = renderPage();

        await user.click(within(table.getAllByRole('row')[1]).getByRole('button', { name: /Cambiar/ }));
        const dialog = within(await screen.findByRole('dialog'));

        expect(dialog.getByLabelText(/Fecha y hora/)).toHaveValue('2026-11-20T09:30');
        expect(dialog.getByLabelText(/Lugar/)).toHaveValue('Auditorio Jorge Artel');
        expect(dialog.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();
    });

    it('quita una cita después de confirmar, sin correo', async () => {
        const user = userEvent.setup();
        renderPage();

        // Tabla y tarjeta pintan el mismo botón: vale cualquiera.
        await user.click(screen.getAllByRole('button', { name: 'Quitar la sustentación de BioSensor' })[0]);
        const confirm = within(await screen.findByRole('alertdialog'));
        expect(confirm.getByText(/No se envía ningún correo/)).toBeInTheDocument();
        await user.click(confirm.getByRole('button', { name: 'Quitar' }));

        await waitFor(() => expect(schedule.mutateAsync).toHaveBeenCalledWith({ projectId: 10 }));
    });

    it('recuerda a los jurados de la cátedra elegida, después de confirmar', async () => {
        renderPage();

        await userEvent.click(screen.getByRole('button', { name: /Recordar a los jurados/ }));
        const dialog = within(screen.getByRole('alertdialog'));
        expect(dialog.getByText(/INNPRENDE I · Despegue/)).toBeInTheDocument();
        expect(remind.mutateAsync).not.toHaveBeenCalled();

        await userEvent.click(dialog.getByRole('button', { name: 'Enviar recordatorio' }));

        await waitFor(() => expect(remind.mutateAsync).toHaveBeenCalledWith({ editionId: '1', track: 'INNPRENDE_I' }));
    });

    it('sin proyectos en la cátedra lo dice', () => {
        useEditions.mockReturnValue({ data: [openEdition], isPending: false });
        useWholeProjectDirectory.mockReturnValue({ data: [], isPending: false, error: null, refetch: vi.fn() });
        usePresentationAgenda.mockReturnValue({ data: [], isPending: false, error: null, refetch: vi.fn() });
        renderWithProviders(<PresentationsPage />);

        expect(screen.getByText('No hay proyectos inscritos en esta cátedra')).toBeInTheDocument();
    });

    it('si la API falla ofrece reintentar', () => {
        useEditions.mockReturnValue({ data: [openEdition], isPending: false });
        useWholeProjectDirectory.mockReturnValue({
            data: undefined,
            isPending: false,
            error: apiError('Error', 500),
            refetch: vi.fn(),
        });
        usePresentationAgenda.mockReturnValue({ data: undefined, isPending: false, error: null, refetch: vi.fn() });
        renderWithProviders(<PresentationsPage />);

        expect(screen.getByText('No pudimos cargar la agenda')).toBeInTheDocument();
    });
});
