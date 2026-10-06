import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, renderWithProviders } from '@/test/utils';
import { JurorsPanel } from './JurorsPanel';
import { useJurors, useSaveJuror } from './queries';

vi.mock('./queries', () => ({ useJurors: vi.fn(), useSaveJuror: vi.fn() }));

const marta = { id: 3, userId: 8, fullName: 'Marta Ríos', email: 'marta@empresa.com', role: 'JUDGE' };

let save;

const renderPanel = (jurors = [marta]) => {
    useJurors.mockReturnValue({ data: jurors, isPending: false, error: null, refetch: vi.fn() });
    renderWithProviders(<JurorsPanel projectId={10} />);
};

beforeEach(() => {
    save = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    useSaveJuror.mockReturnValue(save);
});

describe('Jurados de un proyecto', () => {
    it('lista los asignados con su rol', () => {
        renderPanel();

        expect(screen.getByText('Marta Ríos')).toBeInTheDocument();
        expect(screen.getByText('Jurado')).toBeInTheDocument();
        expect(screen.getByText('1 asignado')).toBeInTheDocument();
    });

    it('sin jurados lo dice', () => {
        renderPanel([]);

        expect(screen.getByText('Este proyecto todavía no tiene jurados.')).toBeInTheDocument();
    });

    it('asigna por correo, también externo', async () => {
        const user = userEvent.setup();
        renderPanel();

        await user.type(screen.getByLabelText(/Asignar jurado/), 'otro@empresa.com');
        await user.click(screen.getByRole('button', { name: /Asignar/ }));

        await waitFor(() => expect(save.mutateAsync).toHaveBeenCalledWith({ email: 'otro@empresa.com' }));
    });

    it('muestra en el campo el motivo que devuelve la API', async () => {
        const user = userEvent.setup();
        save.mutateAsync.mockRejectedValue(
            apiError('Datos inválidos', 400, {
                fields: { email: 'El profesor del grupo no puede ser jurado de su propio proyecto' },
            }),
        );
        renderPanel();

        await user.type(screen.getByLabelText(/Asignar jurado/), 'carlos@unisimon.edu.co');
        await user.click(screen.getByRole('button', { name: /Asignar/ }));

        expect(await screen.findByText('El profesor del grupo no puede ser jurado de su propio proyecto')).toBeInTheDocument();
    });

    it('quita a un jurado después de confirmar', async () => {
        const user = userEvent.setup();
        renderPanel();

        await user.click(screen.getByRole('button', { name: 'Quitar a Marta Ríos como jurado' }));
        const dialog = within(await screen.findByRole('alertdialog'));
        await user.click(dialog.getByRole('button', { name: 'Quitar' }));

        await waitFor(() => expect(save.mutateAsync).toHaveBeenCalledWith({ userId: 8 }));
    });
});
