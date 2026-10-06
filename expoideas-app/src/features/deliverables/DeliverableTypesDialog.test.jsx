import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { openEdition } from '@/test/fixtures';
import { apiError, renderWithProviders } from '@/test/utils';
import { DeliverableTypesDialog } from './DeliverableTypesDialog';
import { downloadFile } from '@/lib/files';
import { useCatalogItems } from '@/features/catalogs/queries';
import { useDeliverableTemplate, useDeliverableTypes, useSaveDeliverableType } from './queries';

vi.mock('./queries', () => ({
    useDeliverableTypes: vi.fn(),
    useSaveDeliverableType: vi.fn(),
    useDeliverableTemplate: vi.fn(),
}));
vi.mock('@/lib/files', async (importOriginal) => ({ ...(await importOriginal()), downloadFile: vi.fn() }));
vi.mock('@/features/catalogs/queries', () => ({ useCatalogItems: vi.fn() }));

const poster = {
    id: 5,
    editionId: 1,
    track: 'INNPRENDE_I',
    name: 'Póster de investigación',
    description: 'Formato oficial, en PDF.',
    kind: 'DOCUMENT',
    required: true,
    maxFiles: 1,
    sortOrder: 1,
};

let save;
let template;

const renderDialog = (types = [poster], track = 'INNPRENDE_I') => {
    useDeliverableTypes.mockReturnValue({ data: types, isPending: false, error: null, refetch: vi.fn() });
    useCatalogItems.mockReturnValue({
        data: [
            { id: 1, name: 'Digital' },
            { id: 2, name: 'Físico' },
        ],
        isPending: false,
    });
    renderWithProviders(<DeliverableTypesDialog edition={openEdition} track={track} onClose={vi.fn()} />);
    return within(screen.getByRole('dialog'));
};

beforeEach(() => {
    save = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    useSaveDeliverableType.mockReturnValue(save);
    template = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    useDeliverableTemplate.mockReturnValue(template);
});

describe('Entregables de una cátedra', () => {
    it('lista lo configurado con sus condiciones', () => {
        const dialog = renderDialog();

        expect(dialog.getByText('Entregables de INNPRENDE I · Despegue')).toBeInTheDocument();
        expect(dialog.getByText('Póster de investigación')).toBeInTheDocument();
        expect(dialog.getByText('Obligatorio')).toBeInTheDocument();
        expect(dialog.getByText('PDF')).toBeInTheDocument();
        expect(dialog.getByText('Un archivo')).toBeInTheDocument();
    });

    it('sin entregables invita a agregar el primero', () => {
        const dialog = renderDialog([]);

        expect(dialog.getByText('Esta cátedra todavía no pide entregables')).toBeInTheDocument();
    });

    it('agrega uno nuevo con la edición y la cátedra de donde se abrió', async () => {
        const dialog = renderDialog([]);

        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));
        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Fotos del prototipo');
        await userEvent.type(dialog.getByLabelText(/Descripción/), 'Tres fotos del prototipo funcionando.');
        await userEvent.selectOptions(dialog.getByLabelText(/Archivos aceptados/), 'IMAGE');
        await userEvent.clear(dialog.getByLabelText(/Máximo de archivos/));
        await userEvent.type(dialog.getByLabelText(/Máximo de archivos/), '3');
        await userEvent.click(dialog.getByRole('button', { name: 'Agregar' }));

        await waitFor(() =>
            expect(save.mutateAsync).toHaveBeenCalledWith({
                id: undefined,
                body: {
                    editionId: 1,
                    track: 'INNPRENDE_I',
                    name: 'Fotos del prototipo',
                    description: 'Tres fotos del prototipo funcionando.',
                    kind: 'IMAGE',
                    required: true,
                    maxFiles: 3,
                    sortOrder: 0,
                    closesOn: null,
                    prototypeTypeId: null,
                },
            }),
        );
    });

    it('un nombre repetido se muestra en su campo', async () => {
        save.mutateAsync.mockRejectedValue(apiError('El recurso ya existe', 409));
        const dialog = renderDialog();

        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));
        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Póster de investigación');
        await userEvent.click(dialog.getByRole('button', { name: 'Agregar' }));

        expect(await dialog.findByText('Ya hay un entregable con ese nombre')).toBeInTheDocument();
    });

    it('editar parte de los datos del entregable', async () => {
        const dialog = renderDialog();

        await userEvent.click(dialog.getByRole('button', { name: 'Editar Póster de investigación' }));

        expect(dialog.getByLabelText(/Nombre/)).toHaveValue('Póster de investigación');
        expect(dialog.getByLabelText(/Máximo de archivos/)).toHaveValue(1);
    });

    it('un entregable con cierre propio lo muestra y se edita con su fecha', async () => {
        const dialog = renderDialog([{ ...poster, closesOn: '2026-12-04' }]);

        expect(dialog.getByText(/Cierra el 4 de dic/)).toBeInTheDocument();
        await userEvent.click(dialog.getByRole('button', { name: 'Editar Póster de investigación' }));
        expect(dialog.getByLabelText(/Cierre propio/)).toHaveValue('2026-12-04');
    });

    it('un entregable de tipo enlace habla de enlaces y no lleva plantilla', async () => {
        const dialog = renderDialog([{ ...poster, kind: 'LINK', name: 'Video del pitch', maxFiles: 2 }]);

        expect(dialog.getByText('Enlace (video, prototipo en línea)')).toBeInTheDocument();
        expect(dialog.getByText('Hasta 2 enlaces')).toBeInTheDocument();
        expect(dialog.queryByRole('button', { name: /Subir plantilla/ })).not.toBeInTheDocument();

        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));
        await userEvent.selectOptions(dialog.getByLabelText(/Archivos aceptados/), 'LINK');
        expect(dialog.getByLabelText(/Máximo de enlaces/)).toBeInTheDocument();
    });

    it('eliminar pide confirmación', async () => {
        const dialog = renderDialog();

        await userEvent.click(dialog.getByRole('button', { name: 'Eliminar Póster de investigación' }));
        const confirm = within(await screen.findByRole('alertdialog'));
        await userEvent.click(confirm.getByRole('button', { name: 'Eliminar' }));

        await waitFor(() => expect(save.mutateAsync).toHaveBeenCalledWith({ id: 5, remove: true }));
    });
});

describe('La plantilla de un entregable', () => {
    it('se sube desde su fila', async () => {
        const dialog = renderDialog();

        await userEvent.upload(
            dialog.getByLabelText('Subir plantilla para Póster de investigación'),
            new File(['%PDF-1.7'], 'formato-poster.pdf', { type: 'application/pdf' }),
        );

        await waitFor(() => expect(template.mutateAsync).toHaveBeenCalledWith({ id: 5, file: expect.any(File) }));
    });

    it('cuando ya hay una, se descarga, se reemplaza o se quita', async () => {
        const dialog = renderDialog([
            { ...poster, templateFileId: '2b2b2b2b-0000-4000-8000-000000000000', templateFileName: 'formato-poster.pptx' },
        ]);

        expect(dialog.queryByRole('button', { name: /Subir plantilla/ })).not.toBeInTheDocument();
        await userEvent.click(dialog.getByRole('button', { name: /formato-poster.pptx/ }));
        expect(downloadFile).toHaveBeenCalledWith('2b2b2b2b-0000-4000-8000-000000000000', 'formato-poster.pptx');

        await userEvent.click(dialog.getByRole('button', { name: 'Quitar plantilla de Póster de investigación' }));
        await waitFor(() => expect(template.mutateAsync).toHaveBeenCalledWith({ id: 5 }));
    });

    it('un archivo de más de 5 MB no se envía', async () => {
        const dialog = renderDialog();
        const big = new File([new Uint8Array(1)], 'grande.pdf', { type: 'application/pdf' });
        Object.defineProperty(big, 'size', { value: 6 * 1024 * 1024 });

        await userEvent.upload(dialog.getByLabelText('Subir plantilla para Póster de investigación'), big);

        // El aviso sale como toast; lo que importa es que no llegue a la API.
        await waitFor(() => expect(template.mutateAsync).not.toHaveBeenCalled());
    });
});

describe('Entregables por tipo de prototipo', () => {
    it('en INNPRENDE I no se pregunta a qué tipo aplica', async () => {
        const dialog = renderDialog([]);

        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));

        expect(dialog.queryByLabelText(/Aplica a/)).not.toBeInTheDocument();
    });

    it('en INNPRENDE II se puede pedir solo a un tipo de prototipo', async () => {
        const dialog = renderDialog([], 'INNPRENDE_II');

        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));
        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Video del prototipo');
        await userEvent.selectOptions(dialog.getByLabelText(/Aplica a/), '1');
        await userEvent.click(dialog.getByRole('button', { name: 'Agregar' }));

        await waitFor(() =>
            expect(save.mutateAsync).toHaveBeenCalledWith({
                id: undefined,
                body: expect.objectContaining({ track: 'INNPRENDE_II', prototypeTypeId: 1 }),
            }),
        );
    });

    it('un error que no es de ningún campo se ve dentro del formulario', async () => {
        save.mutateAsync.mockRejectedValue(apiError('Error del servidor. Intenta nuevamente más tarde.', 500));
        const dialog = renderDialog([]);

        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));
        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Video del pitch');
        await userEvent.click(dialog.getByRole('button', { name: 'Agregar' }));

        expect(await dialog.findByText('Error del servidor. Intenta nuevamente más tarde.')).toBeInTheDocument();
        // El formulario sigue abierto con lo escrito.
        expect(dialog.getByLabelText(/Nombre/)).toHaveValue('Video del pitch');
    });

    it('lo que la API rechaza de un campo que aquí no se pregunta también se ve', async () => {
        save.mutateAsync.mockRejectedValue(
            apiError('Los datos enviados no son válidos.', 400, { fields: { sortOrder: 'El orden es obligatorio' } }),
        );
        const dialog = renderDialog([]);

        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));
        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Video del pitch');
        await userEvent.click(dialog.getByRole('button', { name: 'Agregar' }));

        expect(await dialog.findByText('El orden es obligatorio')).toBeInTheDocument();
    });

    it('cerrar con un entregable a medio escribir pregunta antes de perderlo', async () => {
        const dialog = renderDialog([]);
        await userEvent.click(dialog.getByRole('button', { name: /Agregar entregable/ }));
        await userEvent.type(dialog.getByLabelText(/Nombre/), 'Video del pitch');

        await userEvent.click(dialog.getByRole('button', { name: 'Listo' }));

        const question = within(screen.getByRole('alertdialog'));
        expect(question.getByText('¿Descartar lo que escribiste?')).toBeInTheDocument();
        await userEvent.click(question.getByRole('button', { name: 'Seguir editando' }));
        expect(screen.getByLabelText(/Nombre/)).toHaveValue('Video del pitch');

        // Cancelar el formulario también pregunta; al descartar, se cierra solo el formulario.
        await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
        await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Descartar' }));
        expect(screen.queryByLabelText(/Nombre/)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Agregar entregable/ })).toBeInTheDocument();
    });

    it('un entregable de un tipo lo dice en su fila', () => {
        const dialog = renderDialog([{ ...poster, prototypeTypeId: 2, prototypeType: 'Físico' }], 'INNPRENDE_II');

        expect(dialog.getByText('Solo Físico')).toBeInTheDocument();
    });
});
