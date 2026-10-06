import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useAuth } from '@/features/auth/useAuth';
import { useCatalogItems } from '@/features/catalogs/queries';
import { useEditions } from '@/features/editions/queries';
import { openEdition } from '@/test/fixtures';
import { apiError, renderWithProviders, sessionFor } from '@/test/utils';
import ProjectsPage from './ProjectsPage';
import { downloadProjectsCsv, useProjectDirectory } from './directory';
import { useTeachers } from './queries';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/editions/queries', () => ({ useEditions: vi.fn() }));
vi.mock('@/features/catalogs/queries', () => ({ useCatalogItems: vi.fn() }));
vi.mock('./queries', () => ({ useTeachers: vi.fn() }));
vi.mock('./directory', () => ({ useProjectDirectory: vi.fn(), downloadProjectsCsv: vi.fn() }));

const summary = {
    id: 10,
    editionId: 1,
    edition: 'Expoideas 2026-2',
    track: 'INNPRENDE_I',
    title: 'BioSensor IoT',
    sector: 'Agroindustria y alimentos',
    teacher: 'Carlos Mendoza',
    leader: 'Ana María Pérez',
    members: 3,
    minMembers: 2,
    requiredDeliverables: 2,
    deliveredDeliverables: 1,
    createdAt: '2026-11-04T09:00:00',
};

/** Lo que devuelve la API: una página del listado y cuántas hay. */
const pageOf = (items, { page = 0, totalPages = 1, totalItems = items.length } = {}) => ({
    items,
    page,
    size: 25,
    totalItems,
    totalPages,
});

const renderPage = ({ projects = [summary], role = 'MACONDOLAB', query = {}, paging } = {}) => {
    useAuth.mockReturnValue(sessionFor({ role }));
    useProjectDirectory.mockReturnValue({
        data: pageOf(projects, paging),
        isPending: false,
        error: null,
        refetch: vi.fn(),
        ...query,
    });
    useEditions.mockReturnValue({ data: [openEdition], isPending: false });
    useCatalogItems.mockReturnValue({ data: [{ id: 3, name: 'Agroindustria y alimentos' }], isPending: false });
    useTeachers.mockReturnValue({ data: [{ id: 7, fullName: 'Carlos Mendoza', faculty: 'Ingeniería' }], isPending: false });
    renderWithProviders(<ProjectsPage />);
};

/** Último filtro con el que se pidió la lista, y la página. */
const lastFilters = () => useProjectDirectory.mock.calls.at(-1)[0];
const lastPaging = () => useProjectDirectory.mock.calls.at(-1)[1];

beforeEach(() => {
    downloadProjectsCsv.mockResolvedValue(undefined);
});

describe('Listado para la gestión', () => {
    it('muestra cada proyecto con su equipo y sus entregables', () => {
        renderPage();
        const table = within(screen.getByRole('table'));

        expect(table.getByRole('link', { name: 'BioSensor IoT' })).toHaveAttribute('href', '/proyectos/10');
        expect(table.getByText(/Expoideas 2026-2 · INNPRENDE I · Despegue/)).toBeInTheDocument();
        expect(table.getByText('Carlos Mendoza')).toBeInTheDocument();
        expect(table.getByText('Faltan 1')).toBeInTheDocument();
        expect(screen.getByText('1 proyecto')).toBeInTheDocument();
    });

    it('en móvil el mismo proyecto sale como tarjeta, no como fila de tabla', () => {
        renderPage();
        // Sin CSS se pintan las dos listas; la tarjeta es la que se ve en móvil.
        const cards = within(screen.getByRole('list', { name: 'Proyectos' }));

        expect(cards.getByRole('link', { name: 'BioSensor IoT' })).toHaveAttribute('href', '/proyectos/10');
        expect(cards.getByText('Agroindustria y alimentos')).toBeInTheDocument();
        expect(cards.getByText('Carlos Mendoza')).toBeInTheDocument();
        expect(cards.getByText('Ana María Pérez')).toBeInTheDocument();
        expect(cards.getByText('Faltan 1')).toBeInTheDocument();
    });

    it('avisa cuando el equipo no llega al mínimo', () => {
        renderPage({ projects: [{ ...summary, members: 1 }] });

        expect(screen.getAllByText(/1 integrante · mínimo 2/)).toHaveLength(2);
    });

    it('marca como entregado al que completó lo obligatorio', () => {
        renderPage({ projects: [{ ...summary, deliveredDeliverables: 2 }] });

        expect(screen.getAllByText('Entregado')).toHaveLength(2);
    });

    it('filtra por cátedra y por sector', async () => {
        renderPage();

        await userEvent.selectOptions(screen.getByLabelText('Cátedra'), 'INNPRENDE_II');
        await userEvent.selectOptions(screen.getByLabelText('Sector'), '3');

        await waitFor(() => expect(lastFilters()).toMatchObject({ track: 'INNPRENDE_II', sectorId: '3' }));
    });

    it('muestra la nota del proyecto y cuántos jurados van, o un guion si nadie ha calificado', () => {
        const graded = { ...summary, grade: 4.3, scale: 'GOOD', jurors: 3, evaluated: 2 };
        renderPage({ projects: [graded, { ...summary, id: 11, title: 'Riego', grade: null, jurors: 1, evaluated: 0 }] });

        const table = within(screen.getByRole('table'));
        expect(table.getByText('4.3')).toBeInTheDocument();
        expect(table.getByText('· Bueno')).toBeInTheDocument();
        expect(table.getByText('2 de 3 jurados')).toBeInTheDocument();
        expect(table.getByTitle('Ningún jurado ha calificado')).toBeInTheDocument();
    });

    it('muestra el resultado y filtra por él', async () => {
        renderPage({
            projects: [
                { ...summary, result: 'APPROVED' },
                { ...summary, id: 11, title: 'Riego', result: null },
            ],
        });
        const table = within(screen.getByRole('table'));

        expect(table.getByText('Aprobado')).toBeInTheDocument();
        // El guion del resultado; los de la nota llevan título y se cuentan aparte.
        expect(table.getAllByText('—').filter((cell) => !cell.title)).toHaveLength(1);

        await userEvent.selectOptions(screen.getByLabelText('Resultado'), 'NOT_APPROVED');
        await waitFor(() => expect(lastFilters()).toMatchObject({ result: 'NOT_APPROVED' }));
    });

    it('descarga el CSV con los filtros puestos', async () => {
        renderPage();

        await userEvent.selectOptions(screen.getByLabelText('Cátedra'), 'INNPRENDE_I');
        await userEvent.click(screen.getByRole('button', { name: /Descargar CSV/ }));

        await waitFor(() => expect(downloadProjectsCsv).toHaveBeenCalledWith(expect.objectContaining({ track: 'INNPRENDE_I' })));
    });

    it('sin proyectos lo dice, y con filtros ofrece quitarlos', async () => {
        renderPage({ projects: [] });
        expect(screen.getByText('Todavía no hay proyectos inscritos')).toBeInTheDocument();

        await userEvent.selectOptions(screen.getByLabelText('Cátedra'), 'INNPRENDE_I');

        expect(await screen.findByText('Sin resultados')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Quitar filtros' }));
        expect(screen.getByText('Todavía no hay proyectos inscritos')).toBeInTheDocument();
    });

    it('si la API falla ofrece reintentar', () => {
        renderPage({ projects: [], query: { data: undefined, error: apiError('Error del servidor', 500) } });

        expect(screen.getByText('No pudimos cargar los proyectos')).toBeInTheDocument();
    });
});

describe('El listado por páginas', () => {
    it('pide de a 25 y, si todo cabe en una página, no ofrece pasar a otra', () => {
        renderPage();

        expect(lastPaging()).toEqual({ page: 0, size: 25 });
        expect(screen.queryByRole('navigation', { name: 'Páginas de proyectos' })).not.toBeInTheDocument();
    });

    it('cuenta todos los proyectos, no solo los de la página, y pasa a la siguiente', async () => {
        renderPage({ paging: { totalPages: 3, totalItems: 61 } });
        const pages = within(screen.getByRole('navigation', { name: 'Páginas de proyectos' }));

        expect(screen.getByText('61 proyectos')).toBeInTheDocument();
        expect(pages.getByText('Página 1 de 3 · 61 en total')).toBeInTheDocument();
        expect(pages.getByRole('button', { name: 'Anterior' })).toBeDisabled();

        await userEvent.click(pages.getByRole('button', { name: 'Siguiente' }));

        await waitFor(() => expect(lastPaging()).toEqual({ page: 1, size: 25 }));
        expect(pages.getByText('Página 2 de 3 · 61 en total')).toBeInTheDocument();
    });

    it('un filtro nuevo empieza por la primera página', async () => {
        renderPage({ paging: { totalPages: 3, totalItems: 61 } });
        await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
        await waitFor(() => expect(lastPaging().page).toBe(1));

        await userEvent.selectOptions(screen.getByLabelText('Cátedra'), 'INNPRENDE_II');

        await waitFor(() => expect(lastFilters()).toMatchObject({ track: 'INNPRENDE_II' }));
        expect(lastPaging().page).toBe(0);
    });

    it('si la página en la que estaba deja de existir, vuelve a la última que hay', async () => {
        renderPage({ paging: { totalPages: 3, totalItems: 61 } });
        await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
        await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
        await waitFor(() => expect(lastPaging().page).toBe(2));

        // Alguien eliminó proyectos: ahora solo hay dos páginas y la tercera llega vacía.
        useProjectDirectory.mockReturnValue({
            data: pageOf([], { page: 2, totalPages: 2, totalItems: 50 }),
            isPending: false,
            error: null,
            refetch: vi.fn(),
        });
        await userEvent.click(screen.getByRole('button', { name: /Descargar CSV/ }));

        await waitFor(() => expect(lastPaging().page).toBe(1));
    });
});

describe('Listado para un profesor', () => {
    it('no ofrece filtrar por profesor, porque solo ve los suyos', () => {
        renderPage({ role: 'TEACHER' });

        expect(screen.queryByLabelText('Profesor')).not.toBeInTheDocument();
        expect(screen.getByText(/te nombraron como profesor del grupo/)).toBeInTheDocument();
    });

    it('cada proyecto tiene un botón «Ver proyecto» que abre su ficha, en la tabla y en la tarjeta', () => {
        renderPage({ role: 'TEACHER' });

        const table = within(screen.getByRole('table'));
        const cards = within(screen.getByRole('list', { name: 'Proyectos' }));
        expect(table.getByRole('link', { name: /Ver proyecto/ })).toHaveAttribute('href', '/proyectos/10');
        expect(cards.getByRole('link', { name: /Ver proyecto/ })).toHaveAttribute('href', '/proyectos/10');
    });
});
