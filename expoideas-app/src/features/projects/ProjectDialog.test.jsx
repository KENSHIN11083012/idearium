import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useCatalogItems } from '@/features/catalogs/queries';
import { useEditions } from '@/features/editions/queries';
import { openEdition, project } from '@/test/fixtures';
import { renderWithProviders } from '@/test/utils';
import { ProjectDialog } from './ProjectDialog';
import { useSaveProject, useTeachers } from './queries';

vi.mock('@/features/editions/queries', () => ({ useEditions: vi.fn() }));
vi.mock('@/features/catalogs/queries', () => ({ useCatalogItems: vi.fn() }));
vi.mock('./queries', () => ({ useSaveProject: vi.fn(), useTeachers: vi.fn() }));

const teachers = [{ id: 7, fullName: 'Carlos Mendoza', faculty: 'Ingeniería' }];
const sectors = [
    { id: 3, name: 'Agroindustria y alimentos' },
    { id: 4, name: 'Salud y bienestar' },
];

let save;

let prototypeTypes = [];

const renderDialog = (current = null, editions = [openEdition]) => {
    useEditions.mockReturnValue({ data: editions, isPending: false });
    useCatalogItems.mockImplementation((path) => ({
        data: path === '/prototype-types' ? prototypeTypes : sectors,
        isPending: false,
    }));
    useTeachers.mockReturnValue({ data: teachers, isPending: false });
    renderWithProviders(<ProjectDialog project={current} onClose={vi.fn()} />);
    return within(screen.getByRole('dialog'));
};

beforeEach(() => {
    prototypeTypes = [];
    save = { mutateAsync: vi.fn().mockResolvedValue(project), isPending: false };
    useSaveProject.mockReturnValue(save);
});

describe('Inscribir un proyecto', () => {
    it('envía los datos con los ids como números', async () => {
        const dialog = renderDialog();

        await userEvent.selectOptions(dialog.getByLabelText(/Edición/), '1');
        await userEvent.selectOptions(dialog.getByLabelText(/Cátedra/), 'INNPRENDE_I');
        await userEvent.type(dialog.getByLabelText(/Título/), 'BioSensor');
        await userEvent.type(dialog.getByLabelText(/Propuesta de valor/), 'Sensores para detectar plagas.');
        await userEvent.selectOptions(dialog.getByLabelText(/Sector/), '3');
        await userEvent.selectOptions(dialog.getByLabelText(/Profesor/), '7');
        await userEvent.selectOptions(dialog.getByLabelText(/Semestre que cursas/), '5');
        // Como lo escribe la gente: con espacios y el indicativo. A la API le llegan los diez dígitos.
        await userEvent.type(dialog.getByLabelText(/Tu celular/), '+57 300 123 4567');
        await userEvent.click(dialog.getByRole('button', { name: 'Inscribir' }));

        await waitFor(() =>
            expect(save.mutateAsync).toHaveBeenCalledWith({
                id: undefined,
                body: {
                    editionId: 1,
                    track: 'INNPRENDE_I',
                    title: 'BioSensor',
                    summary: 'Sensores para detectar plagas.',
                    sectorId: 3,
                    prototypeTypeId: null,
                    teacherId: 7,
                    semester: 5,
                    phone: '3001234567',
                },
            }),
        );
    });

    it('no acepta un celular que no tenga diez dígitos', async () => {
        const dialog = renderDialog();

        await userEvent.type(dialog.getByLabelText(/Tu celular/), '300 123');
        await userEvent.click(dialog.getByRole('button', { name: 'Inscribir' }));

        expect(await dialog.findByText('Escribe un celular de 10 dígitos, por ejemplo 3001234567')).toBeInTheDocument();
        expect(save.mutateAsync).not.toHaveBeenCalled();
    });

    it('solo ofrece las ediciones con inscripciones abiertas', () => {
        const dialog = renderDialog(null, [
            openEdition,
            { ...openEdition, id: 2, name: 'Expoideas 2026-1', registrationOpen: false },
        ]);

        const options = within(dialog.getByLabelText(/Edición/))
            .getAllByRole('option')
            .map((option) => option.textContent);
        expect(options).toEqual(['Selecciona la edición', 'Expoideas 2026-2']);
    });

    it('pide los datos que faltan', async () => {
        const dialog = renderDialog();

        await userEvent.click(dialog.getByRole('button', { name: 'Inscribir' }));

        // Por rol: "Selecciona la edición" también es el texto de una opción del select.
        await dialog.findAllByRole('alert');
        const errors = dialog.getAllByRole('alert').map((error) => error.textContent);
        expect(errors).toEqual(
            expect.arrayContaining([
                'Selecciona la edición',
                'Ingresa el título del proyecto de innovación',
                'Selecciona el profesor del grupo',
                'Selecciona el semestre que cursas',
                'Ingresa tu celular',
            ]),
        );
        expect(save.mutateAsync).not.toHaveBeenCalled();
    });
});

describe('Editar un proyecto', () => {
    it('parte de sus datos y no deja cambiar la cátedra', () => {
        const dialog = renderDialog(project);

        expect(dialog.getByLabelText(/Título/)).toHaveValue('BioSensor');
        expect(dialog.getByLabelText(/Sector/)).toHaveValue('3');
        // Los datos del líder son los que dio al inscribir, y los puede corregir.
        expect(dialog.getByLabelText(/Semestre que cursas/)).toHaveValue('5');
        expect(dialog.getByLabelText(/Tu celular/)).toHaveValue('3001234567');
        expect(dialog.queryByLabelText(/Cátedra/)).not.toBeInTheDocument();
        expect(dialog.queryByLabelText(/Edición/)).not.toBeInTheDocument();
    });

    it('guarda los cambios sobre el mismo proyecto', async () => {
        const dialog = renderDialog(project);

        await userEvent.clear(dialog.getByLabelText(/Título/));
        await userEvent.type(dialog.getByLabelText(/Título/), 'BioSensor v2');
        await userEvent.click(dialog.getByRole('button', { name: 'Guardar cambios' }));

        await waitFor(() => expect(save.mutateAsync).toHaveBeenCalledWith(expect.objectContaining({ id: 10 })));
    });
});

describe('Tipo de prototipo', () => {
    it('no se pide en INNPRENDE I ni cuando el catálogo está vacío', async () => {
        const dialog = renderDialog();

        await userEvent.selectOptions(dialog.getByLabelText(/Cátedra/), 'INNPRENDE_II');
        expect(dialog.queryByLabelText(/Tipo de prototipo/)).not.toBeInTheDocument();
    });

    it('en INNPRENDE II con catálogo cargado se elige y viaja como número', async () => {
        prototypeTypes = [
            { id: 1, name: 'Digital' },
            { id: 2, name: 'Físico' },
        ];
        const dialog = renderDialog();

        await userEvent.selectOptions(dialog.getByLabelText(/Cátedra/), 'INNPRENDE_I');
        expect(dialog.queryByLabelText(/Tipo de prototipo/)).not.toBeInTheDocument();

        await userEvent.selectOptions(dialog.getByLabelText(/Edición/), '1');
        await userEvent.selectOptions(dialog.getByLabelText(/Cátedra/), 'INNPRENDE_II');
        await userEvent.type(dialog.getByLabelText(/Título/), 'Prototipo BioSensor');
        await userEvent.type(dialog.getByLabelText(/Propuesta de valor/), 'Un prototipo para validar en campo.');
        await userEvent.selectOptions(dialog.getByLabelText(/Sector/), '3');
        await userEvent.selectOptions(dialog.getByLabelText(/Tipo de prototipo/), '2');
        await userEvent.selectOptions(dialog.getByLabelText(/Profesor/), '7');
        await userEvent.selectOptions(dialog.getByLabelText(/Semestre que cursas/), '8');
        await userEvent.type(dialog.getByLabelText(/Tu celular/), '3001234567');
        await userEvent.click(dialog.getByRole('button', { name: 'Inscribir' }));

        await waitFor(() =>
            expect(save.mutateAsync).toHaveBeenCalledWith({
                id: undefined,
                body: expect.objectContaining({ track: 'INNPRENDE_II', prototypeTypeId: 2 }),
            }),
        );
    });
});
