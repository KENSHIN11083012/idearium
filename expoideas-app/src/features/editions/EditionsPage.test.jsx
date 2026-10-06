import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError } from '@/test/utils';
import EditionsPage from './EditionsPage';
import { useEditions, usePublishGrades, useSaveEdition } from './queries';

vi.mock('./queries', () => ({ useEditions: vi.fn(), useSaveEdition: vi.fn(), usePublishGrades: vi.fn() }));

/** Edición en plena inscripción, como la devuelve la API. */
const open = {
    id: 1,
    name: 'Expoideas 2026-2',
    registrationOpensOn: '2026-11-03',
    registrationClosesOn: '2026-11-14',
    submissionClosesOn: '2026-11-28',
    registrationOpen: true,
    submissionOpen: true,
    tracks: [
        { track: 'INNPRENDE_I', minMembers: 2, maxMembers: 5 },
        { track: 'INNPRENDE_II', minMembers: 3, maxMembers: 6 },
    ],
};

let saveEdition;
let publishGrades;

const renderPage = (editions = [open], query = {}) => {
    useEditions.mockReturnValue({ data: editions, isPending: false, error: null, refetch: vi.fn(), ...query });
    render(<EditionsPage />);
};

/** Abre el diálogo y devuelve sus campos. */
const openDialog = async (name = 'Nueva edición') => {
    // Sin ediciones, el botón está en el encabezado y en el estado vacío.
    await userEvent.click(screen.getAllByRole('button', { name })[0]);
    return within(await screen.findByRole('dialog'));
};

beforeEach(() => {
    saveEdition = { mutateAsync: vi.fn().mockResolvedValue(open), isPending: false };
    useSaveEdition.mockReturnValue(saveEdition);
    publishGrades = { mutateAsync: vi.fn().mockResolvedValue(open), isPending: false };
    usePublishGrades.mockReturnValue(publishGrades);
});

describe('Listado de ediciones', () => {
    it('muestra los plazos y los límites de grupo de cada cátedra', () => {
        renderPage();

        expect(screen.getByRole('heading', { level: 2, name: 'Expoideas 2026-2' })).toBeInTheDocument();
        expect(screen.getByText('Inscripciones abiertas')).toBeInTheDocument();
        expect(screen.getByText(/Grupos de 2 a 5 integrantes/)).toBeInTheDocument();
        expect(screen.getByText(/Grupos de 3 a 6 integrantes/)).toBeInTheDocument();
        expect(screen.getByText(/Entregas hasta el/)).toBeInTheDocument();
    });

    it('publica las notas de una cátedra después de confirmar, y las oculta si ya estaban publicadas', async () => {
        renderPage([
            {
                ...open,
                tracks: [
                    { track: 'INNPRENDE_I', minMembers: 2, maxMembers: 5 },
                    { track: 'INNPRENDE_II', minMembers: 3, maxMembers: 6, gradesPublishedAt: '2026-11-25T08:00:00' },
                ],
            },
        ]);

        expect(screen.getByText('Notas sin publicar: los equipos no las ven')).toBeInTheDocument();
        expect(screen.getByText(/Notas publicadas el/)).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: /Publicar notas/ }));
        const dialog = within(screen.getByRole('alertdialog'));
        expect(dialog.getByText(/Cada equipo de INNPRENDE I · Despegue/)).toBeInTheDocument();
        await userEvent.click(dialog.getByRole('button', { name: 'Publicar' }));
        await waitFor(() =>
            expect(publishGrades.mutateAsync).toHaveBeenCalledWith({ id: open.id, track: 'INNPRENDE_I', publish: true }),
        );

        await userEvent.click(screen.getByRole('button', { name: /Ocultar notas/ }));
        await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Ocultar' }));
        await waitFor(() =>
            expect(publishGrades.mutateAsync).toHaveBeenCalledWith({ id: open.id, track: 'INNPRENDE_II', publish: false }),
        );
    });

    it('con las inscripciones cerradas y las entregas abiertas lo dice', () => {
        renderPage([{ ...open, registrationOpen: false, submissionOpen: true }]);

        expect(screen.getByText('Solo entregas')).toBeInTheDocument();
    });

    it('una edición que aún no empieza aparece como próxima', () => {
        renderPage([
            {
                ...open,
                registrationOpensOn: '2099-01-01',
                registrationOpen: false,
                submissionOpen: false,
            },
        ]);

        expect(screen.getByText('Próxima')).toBeInTheDocument();
    });

    it('una edición terminada aparece como cerrada', () => {
        renderPage([
            {
                ...open,
                registrationOpensOn: '2020-01-01',
                registrationClosesOn: '2020-02-01',
                submissionClosesOn: '2020-03-01',
                registrationOpen: false,
                submissionOpen: false,
            },
        ]);

        expect(screen.getByText('Cerrada')).toBeInTheDocument();
    });

    it('sin ediciones invita a crear la del semestre', () => {
        renderPage([]);

        expect(screen.getByText('Todavía no hay ediciones')).toBeInTheDocument();
    });

    it('si la API falla ofrece reintentar', () => {
        renderPage([], { data: undefined, error: apiError('Error del servidor', 500) });

        expect(screen.getByText('No pudimos cargar las ediciones')).toBeInTheDocument();
    });
});

describe('Alta y edición', () => {
    it('crea una edición con las dos cátedras', async () => {
        renderPage([]);
        const dialog = await openDialog();

        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Expoideas 2026-2');
        await userEvent.type(dialog.getByLabelText(/Apertura de inscripciones/), '2026-11-03');
        await userEvent.type(dialog.getByLabelText(/Cierre de inscripciones/), '2026-11-14');
        await userEvent.type(dialog.getByLabelText(/Cierre de entregas/), '2026-11-28');

        const [firstTrack, secondTrack] = dialog.getAllByRole('group');
        await userEvent.type(within(firstTrack).getByLabelText(/Mínimo/), '2');
        await userEvent.type(within(firstTrack).getByLabelText(/Máximo/), '5');
        await userEvent.type(within(secondTrack).getByLabelText(/Mínimo/), '3');
        await userEvent.type(within(secondTrack).getByLabelText(/Máximo/), '6');

        await userEvent.click(dialog.getByRole('button', { name: 'Crear' }));

        await waitFor(() =>
            expect(saveEdition.mutateAsync).toHaveBeenCalledWith({
                id: undefined,
                body: {
                    name: 'Expoideas 2026-2',
                    registrationOpensOn: '2026-11-03',
                    registrationClosesOn: '2026-11-14',
                    submissionClosesOn: '2026-11-28',
                    tracks: [
                        { track: 'INNPRENDE_I', minMembers: 2, maxMembers: 5 },
                        { track: 'INNPRENDE_II', minMembers: 3, maxMembers: 6 },
                    ],
                },
            }),
        );
    });

    it('no deja cerrar las inscripciones antes de abrirlas', async () => {
        renderPage([]);
        const dialog = await openDialog();

        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Expoideas');
        await userEvent.type(dialog.getByLabelText(/Apertura de inscripciones/), '2026-11-10');
        await userEvent.type(dialog.getByLabelText(/Cierre de inscripciones/), '2026-11-03');
        await userEvent.type(dialog.getByLabelText(/Cierre de entregas/), '2026-11-28');
        const [firstTrack, secondTrack] = dialog.getAllByRole('group');
        await userEvent.type(within(firstTrack).getByLabelText(/Mínimo/), '2');
        await userEvent.type(within(firstTrack).getByLabelText(/Máximo/), '5');
        await userEvent.type(within(secondTrack).getByLabelText(/Mínimo/), '2');
        await userEvent.type(within(secondTrack).getByLabelText(/Máximo/), '5');

        await userEvent.click(dialog.getByRole('button', { name: 'Crear' }));

        expect(await dialog.findByText('Las inscripciones no pueden cerrar antes de abrir')).toBeInTheDocument();
        expect(saveEdition.mutateAsync).not.toHaveBeenCalled();
    });

    it('no deja un máximo de integrantes menor que el mínimo', async () => {
        renderPage();
        const dialog = await openDialog('Editar');

        const [firstTrack] = dialog.getAllByRole('group');
        const max = within(firstTrack).getByLabelText(/Máximo/);
        await userEvent.clear(max);
        await userEvent.type(max, '1');
        await userEvent.click(dialog.getByRole('button', { name: 'Guardar cambios' }));

        expect(await dialog.findByText('En INNPRENDE I, el máximo no puede ser menor que el mínimo')).toBeInTheDocument();
        expect(saveEdition.mutateAsync).not.toHaveBeenCalled();
    });

    it('al editar parte de los datos de la edición', async () => {
        renderPage();
        const dialog = await openDialog('Editar');

        expect(dialog.getByLabelText(/Nombre/)).toHaveValue('Expoideas 2026-2');
        expect(dialog.getByLabelText(/Cierre de entregas/)).toHaveValue('2026-11-28');
        const [firstTrack] = dialog.getAllByRole('group');
        expect(within(firstTrack).getByLabelText(/Máximo/)).toHaveValue(5);
    });

    it('un nombre repetido se muestra en su campo', async () => {
        saveEdition.mutateAsync.mockRejectedValue(apiError('El recurso ya existe', 409));
        renderPage();
        const dialog = await openDialog('Editar');

        await userEvent.type(dialog.getByLabelText(/Nombre/), ' bis');
        await userEvent.click(dialog.getByRole('button', { name: 'Guardar cambios' }));

        expect(await dialog.findByText('Ya existe una edición con ese nombre')).toBeInTheDocument();
    });
});
