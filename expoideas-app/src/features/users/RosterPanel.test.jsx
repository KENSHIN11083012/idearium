import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, renderWithProviders } from '@/test/utils';
import { RosterPanel } from './RosterPanel';
import { useClearRoster, useImportRoster, useRemoveRosterEntry, useRoster } from './queries';

vi.mock('./queries', () => ({
    useRoster: vi.fn(),
    useImportRoster: vi.fn(),
    useRemoveRosterEntry: vi.fn(),
    useClearRoster: vi.fn(),
}));

const carlos = {
    id: 1,
    email: 'carlos.mendoza@unisimon.edu.co',
    role: 'TEACHER',
    firstName: 'Carlos',
    lastName: 'Mendoza',
    registered: true,
};
const ana = { id: 2, email: 'ana.perez@unisimon.edu.co', role: 'STUDENT', firstName: null, lastName: null, registered: false };

let importRoster;
let removeEntry;
let clearRoster;

const renderPanel = (entries = [carlos, ana], query = {}) => {
    useRoster.mockReturnValue({ data: entries, isPending: false, error: null, refetch: vi.fn(), ...query });
    renderWithProviders(<RosterPanel />);
};

const csv = (text) => new File([text], 'listado.csv', { type: 'text/csv' });

beforeEach(() => {
    importRoster = { mutateAsync: vi.fn(), isPending: false };
    removeEntry = { mutate: vi.fn(), isPending: false };
    clearRoster = { mutate: vi.fn(), isPending: false };
    useImportRoster.mockReturnValue(importRoster);
    useRemoveRosterEntry.mockReturnValue(removeEntry);
    useClearRoster.mockReturnValue(clearRoster);
});

describe('El listado de la cátedra', () => {
    it('muestra cada persona con su rol y si ya tiene cuenta', () => {
        renderPanel();

        const table = within(screen.getByRole('table'));
        expect(table.getByText('Carlos Mendoza')).toBeInTheDocument();
        expect(table.getByText('Profesor')).toBeInTheDocument();
        expect(table.getByText('Registrado')).toBeInTheDocument();
        expect(table.getByText('ana.perez@unisimon.edu.co')).toBeInTheDocument();
        expect(table.getByText('Sin registrar')).toBeInTheDocument();
        expect(screen.getByText('2 de 2 · 1 con cuenta')).toBeInTheDocument();
    });

    it('carga un CSV y resume qué entró y qué se rechazó', async () => {
        importRoster.mutateAsync.mockResolvedValue({
            added: 2,
            updated: 1,
            registered: 1,
            total: 3,
            rejected: [{ line: 4, email: 'ana@gmail.com', reason: 'El correo debe terminar en @unisimon.edu.co' }],
        });
        renderPanel();

        await userEvent.upload(screen.getByLabelText('Elegir el archivo del listado'), csv('correo;rol\n'));

        await waitFor(() => expect(importRoster.mutateAsync).toHaveBeenCalledTimes(1));
        expect(importRoster.mutateAsync.mock.calls[0][0]).toBeInstanceOf(File);
        expect(await screen.findByText('2 personas nuevas, 1 actualizada, 1 fila rechazada')).toBeInTheDocument();
        expect(screen.getByText(/El listado tiene ahora 3 personas; 1 de las cargadas ya tiene cuenta/)).toBeInTheDocument();
        expect(screen.getByText(/Línea 4 \(ana@gmail.com\): El correo debe terminar en @unisimon.edu.co/)).toBeInTheDocument();
    });

    it('un archivo que la API rechaza se explica sin resumen', async () => {
        importRoster.mutateAsync.mockRejectedValue(
            apiError('Los datos enviados no son válidos.', 400, {
                fields: { file: 'La primera fila debe tener las columnas «correo» y «rol»' },
            }),
        );
        renderPanel([]);

        await userEvent.upload(screen.getByLabelText('Elegir el archivo del listado'), csv('nombre\n'));

        await waitFor(() => expect(importRoster.mutateAsync).toHaveBeenCalledTimes(1));
        expect(screen.queryByText(/personas nuevas/)).not.toBeInTheDocument();
        expect(screen.getByText('Todavía no hay listado')).toBeInTheDocument();
    });

    it('quita una fila, y vacía el listado después de confirmar', async () => {
        renderPanel();

        // Tabla y tarjeta se pintan a la vez en jsdom: cualquiera de los dos botones vale.
        await userEvent.click(screen.getAllByRole('button', { name: 'Quitar del listado a ana.perez@unisimon.edu.co' })[0]);
        expect(removeEntry.mutate).toHaveBeenCalledWith(2, expect.any(Object));

        await userEvent.click(screen.getByRole('button', { name: /Vaciar listado/ }));
        expect(clearRoster.mutate).not.toHaveBeenCalled();
        await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Vaciar' }));
        expect(clearRoster.mutate).toHaveBeenCalledTimes(1);
    });

    it('busca por nombre o correo', async () => {
        renderPanel();

        await userEvent.type(screen.getByLabelText('Buscar en el listado'), 'perez');

        const table = within(screen.getByRole('table'));
        await waitFor(() => expect(table.queryByText('Carlos Mendoza')).not.toBeInTheDocument());
        expect(table.getByText('ana.perez@unisimon.edu.co')).toBeInTheDocument();
    });

    it('sin listado lo dice y ofrece la plantilla', () => {
        renderPanel([]);

        expect(screen.getByText('Todavía no hay listado')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Plantilla/ })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Cargar listado/ })).toBeInTheDocument();
    });
});
