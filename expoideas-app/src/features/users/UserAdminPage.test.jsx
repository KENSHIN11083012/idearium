import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useAuth } from '@/features/auth/useAuth';
import { useAffiliationCatalogs } from '@/features/catalogs/queries';
import { ana, carla, luis, marta } from '@/test/fixtures';
import { apiError } from '@/test/utils';
import UserAdminPage from './UserAdminPage';
import {
    useCreateUser,
    useDeleteUser,
    useResetPassword,
    useSaveTrackApproval,
    useSetSuspended,
    useTrackApprovals,
    useUpdateUser,
    useUsers,
} from './queries';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/catalogs/queries', () => ({ useAffiliationCatalogs: vi.fn() }));
vi.mock('./RosterPanel', () => ({ RosterPanel: () => <section>Panel del listado</section> }));
vi.mock('./queries', () => ({
    useUsers: vi.fn(),
    useCreateUser: vi.fn(),
    useUpdateUser: vi.fn(),
    useResetPassword: vi.fn(),
    useSetSuspended: vi.fn(),
    useDeleteUser: vi.fn(),
    useTrackApprovals: vi.fn(),
    useSaveTrackApproval: vi.fn(),
}));

const mutation = (overrides = {}) => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
    variables: undefined,
    ...overrides,
});

/** Lo que devuelve la API: una página de cuentas y cuántas hay. */
const pageOf = (items, { page = 0, totalPages = 1, totalItems = items.length } = {}) => ({
    items,
    page,
    size: 25,
    totalItems,
    totalPages,
});

/** Los filtros y la página con los que se pidió la lista por última vez. */
const lastRequest = () => useUsers.mock.calls.at(-1)[0];

/** Renderiza la página con la sesión de `me` y devuelve la tabla de escritorio. */
function renderAs(me, role, users = [ana, luis, carla], paging) {
    useAuth.mockReturnValue({ user: { email: me.email }, role });
    useUsers.mockReturnValue({
        data: pageOf(users, paging),
        isPending: false,
        isFetching: false,
        error: null,
        refetch: vi.fn(),
    });
    useAffiliationCatalogs.mockReturnValue({
        campuses: [{ id: 1, name: 'Barranquilla' }],
        faculties: [{ id: 2, name: 'Ingeniería' }],
        programs: [],
        isPending: false,
        error: null,
    });
    render(<UserAdminPage />);
    // La página pinta tabla (escritorio) y tarjetas (celular); sin CSS en jsdom se ven ambas.
    return within(screen.getByRole('table'));
}

const row = (table, name) => table.getByText(name, { exact: false }).closest('tr');

const options = (select) =>
    within(select)
        .getAllByRole('option')
        .map((option) => option.textContent);

let updateUser;
let deleteUser;
let createUser;
let saveApproval;
let setSuspended;

beforeEach(() => {
    updateUser = mutation();
    deleteUser = mutation();
    createUser = mutation();
    saveApproval = mutation({ mutateAsync: vi.fn().mockResolvedValue({}) });
    useUpdateUser.mockReturnValue(updateUser);
    useDeleteUser.mockReturnValue(deleteUser);
    useCreateUser.mockReturnValue(createUser);
    setSuspended = mutation();
    useSetSuspended.mockReturnValue(setSuspended);
    useResetPassword.mockReturnValue(mutation());
    useTrackApprovals.mockReturnValue({ data: [], isPending: false, error: null, refetch: vi.fn() });
    useSaveTrackApproval.mockReturnValue(saveApproval);
});

describe('Usuarios como administrador', () => {
    it('ve todos los roles pero no puede cambiar el suyo', () => {
        const table = renderAs(luis, 'ADMIN');

        expect(options(table.getByRole('combobox', { name: 'Rol de Ana María Pérez' }))).toEqual([
            'Estudiante',
            'Profesor',
            'Jurado',
            'MacondoLab',
            'Administrador',
        ]);
        expect(table.getByRole('combobox', { name: 'Rol de Carla Díaz' })).toBeInTheDocument();
        expect(table.queryByRole('combobox', { name: 'Rol de Luis Gómez' })).not.toBeInTheDocument();
        expect(within(row(table, 'Luis Gómez')).getByText('Administrador')).toBeInTheDocument();
    });

    it('pide confirmación antes de dar un rol de gestión', async () => {
        const user = userEvent.setup();
        const table = renderAs(luis, 'ADMIN');

        await user.selectOptions(table.getByRole('combobox', { name: 'Rol de Ana María Pérez' }), 'MACONDOLAB');

        const confirm = await screen.findByRole('alertdialog');
        expect(within(confirm).getByText('¿Dar el rol MacondoLab a Ana María Pérez?')).toBeInTheDocument();
        expect(updateUser.mutate).not.toHaveBeenCalled();

        await user.click(within(confirm).getByRole('button', { name: 'Dar el rol' }));
        expect(updateUser.mutate).toHaveBeenCalledWith({ id: ana.id, changes: { role: 'MACONDOLAB' } }, expect.anything());
    });

    it('los demás roles se aplican sin confirmar', async () => {
        const user = userEvent.setup();
        const table = renderAs(luis, 'ADMIN');

        await user.selectOptions(table.getByRole('combobox', { name: 'Rol de Ana María Pérez' }), 'JUDGE');

        expect(updateUser.mutate).toHaveBeenCalledWith({ id: ana.id, changes: { role: 'JUDGE' } }, expect.anything());
        expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    });

    it('puede eliminar otras cuentas', async () => {
        const user = userEvent.setup();
        const table = renderAs(luis, 'ADMIN');

        await user.click(table.getByRole('button', { name: 'Acciones para Ana María Pérez' }));

        expect(await screen.findByRole('menuitem', { name: 'Eliminar usuario' })).toBeInTheDocument();
    });
});

describe('La lista de cuentas por páginas', () => {
    it('pide la primera página sin filtros y dice cuántas cuentas hay en total', () => {
        renderAs(luis, 'ADMIN', [ana, luis, carla], { totalPages: 4, totalItems: 87 });

        expect(lastRequest()).toEqual({ search: '', role: '', pendingRole: false, page: 0, size: 25 });
        expect(screen.getByText('87 cuentas')).toBeInTheDocument();
        expect(screen.getByText('Página 1 de 4 · 87 en total')).toBeInTheDocument();
    });

    it('si todas caben en una página no ofrece pasar a otra', () => {
        renderAs(luis, 'ADMIN');

        expect(screen.getByText('3 cuentas')).toBeInTheDocument();
        expect(screen.queryByRole('navigation', { name: 'Páginas de cuentas' })).not.toBeInTheDocument();
    });

    it('la búsqueda y el rol los resuelve la API, y empiezan por la primera página', async () => {
        const user = userEvent.setup();
        renderAs(luis, 'ADMIN', [ana, luis, carla], { totalPages: 4, totalItems: 87 });
        await user.click(screen.getByRole('button', { name: 'Siguiente' }));
        await waitFor(() => expect(lastRequest().page).toBe(1));

        await user.type(screen.getByRole('searchbox', { name: 'Buscar usuarios' }), ' ana ');
        await waitFor(() => expect(lastRequest()).toMatchObject({ search: 'ana', page: 0 }));

        await user.click(screen.getByRole('button', { name: 'Siguiente' }));
        await waitFor(() => expect(lastRequest().page).toBe(1));
        await user.selectOptions(screen.getByRole('combobox', { name: 'Filtrar por rol' }), 'JUDGE');
        await waitFor(() => expect(lastRequest()).toMatchObject({ search: 'ana', role: 'JUDGE', pendingRole: false, page: 0 }));
    });

    it('filtra las cuentas que tienen un rol del listado por confirmar', async () => {
        const user = userEvent.setup();
        renderAs(marta, 'MACONDOLAB');

        await user.selectOptions(screen.getByRole('combobox', { name: 'Filtrar por rol' }), 'Con rol por confirmar');

        await waitFor(() => expect(lastRequest()).toMatchObject({ role: '', pendingRole: true, page: 0 }));
    });
});

describe('Suspender una cuenta', () => {
    it('pide confirmación, porque corta el acceso de inmediato', async () => {
        const user = userEvent.setup();
        const table = renderAs(marta, 'MACONDOLAB');

        await user.click(within(row(table, ana.email)).getByRole('button', { name: /^Acciones para/ }));
        await user.click(await screen.findByRole('menuitem', { name: /Suspender cuenta/ }));

        expect(setSuspended.mutate).not.toHaveBeenCalled();
        const dialog = await screen.findByRole('alertdialog');
        expect(within(dialog).getByText(/no podrá iniciar sesión/)).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: 'Suspender' }));

        expect(setSuspended.mutate).toHaveBeenCalledWith({ id: ana.id, suspended: true }, expect.anything());
    });

    it('una cuenta suspendida lo dice en la lista y se reactiva sin confirmar', async () => {
        const user = userEvent.setup();
        const table = renderAs(marta, 'MACONDOLAB', [{ ...ana, suspended: true }, luis]);

        expect(within(row(table, ana.email)).getByText('Suspendida')).toBeInTheDocument();
        expect(within(row(table, luis.email)).queryByText('Suspendida')).not.toBeInTheDocument();

        await user.click(within(row(table, ana.email)).getByRole('button', { name: /^Acciones para/ }));
        await user.click(await screen.findByRole('menuitem', { name: /Reactivar cuenta/ }));

        expect(setSuspended.mutate).toHaveBeenCalledWith({ id: ana.id, suspended: false }, expect.anything());
    });
});

describe('Rol del listado por confirmar', () => {
    const pending = { ...ana, pendingRole: 'TEACHER' };

    it('la lista dice qué rol pide y la gestión se lo da', async () => {
        const user = userEvent.setup();
        const table = renderAs(marta, 'MACONDOLAB', [pending, luis]);

        expect(within(row(table, ana.email)).getByText('Pide rol: Profesor')).toBeInTheDocument();
        expect(within(row(table, luis.email)).queryByText(/Pide rol/)).not.toBeInTheDocument();

        await user.click(within(row(table, ana.email)).getByRole('button', { name: /^Acciones para/ }));
        await user.click(await screen.findByRole('menuitem', { name: /Dar el rol de Profesor/ }));

        expect(updateUser.mutate).toHaveBeenCalledWith({ id: ana.id, changes: { role: 'TEACHER' } }, expect.anything());
    });

    it('descartarlo deja la cuenta con el rol que tiene', async () => {
        const user = userEvent.setup();
        const table = renderAs(marta, 'MACONDOLAB', [pending]);

        await user.click(within(row(table, ana.email)).getByRole('button', { name: /^Acciones para/ }));
        await user.click(await screen.findByRole('menuitem', { name: /Descartar el rol del listado/ }));

        expect(updateUser.mutate).toHaveBeenCalledWith({ id: ana.id, changes: { role: 'STUDENT' } }, expect.anything());
    });

    it('sin rol pendiente no aparecen esas opciones', async () => {
        const user = userEvent.setup();
        const table = renderAs(marta, 'MACONDOLAB', [ana]);

        await user.click(within(row(table, ana.email)).getByRole('button', { name: /^Acciones para/ }));

        expect(await screen.findByRole('menuitem', { name: /Restablecer contraseña/ })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: /rol del listado/ })).not.toBeInTheDocument();
    });
});

describe('Primer ingreso en la lista', () => {
    it('marca las cuentas que aún no completan su primer ingreso', () => {
        const table = renderAs(carla, 'MACONDOLAB', [ana, marta]);

        expect(within(row(table, 'Marta Ríos')).getByText('Primer ingreso pendiente')).toBeInTheDocument();
        expect(within(row(table, 'Ana María Pérez')).queryByText('Primer ingreso pendiente')).not.toBeInTheDocument();
    });
});

describe('Usuarios como MacondoLab', () => {
    it('no ve controles sobre cuentas de gestión ni sobre la suya', () => {
        const table = renderAs(carla, 'MACONDOLAB');

        expect(table.queryByRole('combobox', { name: 'Rol de Luis Gómez' })).not.toBeInTheDocument();
        expect(table.queryByRole('button', { name: 'Acciones para Luis Gómez' })).not.toBeInTheDocument();
        expect(table.queryByRole('combobox', { name: 'Rol de Carla Díaz' })).not.toBeInTheDocument();
        expect(table.queryByRole('button', { name: 'Acciones para Carla Díaz' })).not.toBeInTheDocument();
    });

    it('solo asigna estudiante, profesor o jurado', () => {
        const table = renderAs(carla, 'MACONDOLAB');

        expect(options(table.getByRole('combobox', { name: 'Rol de Ana María Pérez' }))).toEqual([
            'Estudiante',
            'Profesor',
            'Jurado',
        ]);
    });

    it('no puede eliminar cuentas', async () => {
        const user = userEvent.setup();
        const table = renderAs(carla, 'MACONDOLAB');

        await user.click(table.getByRole('button', { name: 'Acciones para Ana María Pérez' }));

        expect(await screen.findByRole('menuitem', { name: 'Restablecer contraseña' })).toBeInTheDocument();
        expect(screen.getByRole('menuitem', { name: 'Editar adscripción' })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Eliminar usuario' })).not.toBeInTheDocument();
    });
});

describe('Nueva cuenta', () => {
    async function openDialog(user) {
        renderAs(carla, 'MACONDOLAB');
        await user.click(screen.getByRole('button', { name: 'Nueva cuenta' }));
        return within(await screen.findByRole('dialog'));
    }

    async function fill(dialog, user, { email }) {
        await user.type(dialog.getByLabelText(/^Nombres/), 'Marta');
        await user.type(dialog.getByLabelText(/^Apellidos/), 'Ríos');
        await user.type(dialog.getByLabelText(/^Correo/), email);
        await user.type(dialog.getByLabelText(/^Contraseña temporal/), 'Temporal#2026');
    }

    it('sugiere jurado sin adscripción y la pide al elegir profesor', async () => {
        const user = userEvent.setup();
        const dialog = await openDialog(user);

        const role = dialog.getByLabelText(/^Rol/);
        expect(role).toHaveValue('JUDGE');
        expect(options(role)).toEqual(['Estudiante', 'Profesor', 'Jurado']);
        expect(dialog.getByText('Puede ser personal o de su organización.')).toBeInTheDocument();
        expect(dialog.queryByText('Adscripción académica')).not.toBeInTheDocument();

        await user.selectOptions(role, 'TEACHER');

        expect(dialog.getByText('Adscripción académica')).toBeInTheDocument();
        expect(dialog.getByText('Debe terminar en @unisimon.edu.co')).toBeInTheDocument();
    });

    it('a un profesor le exige correo institucional, sede y facultad', async () => {
        const user = userEvent.setup();
        const dialog = await openDialog(user);

        await user.selectOptions(dialog.getByLabelText(/^Rol/), 'TEACHER');
        await fill(dialog, user, { email: 'pedro@gmail.com' });
        await user.click(dialog.getByRole('button', { name: 'Crear cuenta' }));

        await dialog.findAllByRole('alert');
        expect(dialog.getAllByRole('alert').map((alert) => alert.textContent)).toEqual([
            'Usa un correo @unisimon.edu.co; solo los jurados pueden tener uno externo',
            'Selecciona la sede',
            'Selecciona la facultad',
        ]);
        expect(createUser.mutateAsync).not.toHaveBeenCalled();
    });

    it('crea un jurado externo con el cuerpo que espera la API', async () => {
        const user = userEvent.setup();
        createUser.mutateAsync.mockResolvedValue({ id: 9, firstName: 'Marta', lastName: 'Ríos' });
        const dialog = await openDialog(user);

        await fill(dialog, user, { email: 'marta@empresa.com' });
        await user.click(dialog.getByRole('button', { name: 'Crear cuenta' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(createUser.mutateAsync).toHaveBeenCalledWith({
            firstName: 'Marta',
            lastName: 'Ríos',
            email: 'marta@empresa.com',
            password: 'Temporal#2026',
            role: 'JUDGE',
        });
    });

    it('un profesor se crea con su adscripción', async () => {
        const user = userEvent.setup();
        createUser.mutateAsync.mockResolvedValue({ id: 10, firstName: 'Marta', lastName: 'Ríos' });
        const dialog = await openDialog(user);

        await user.selectOptions(dialog.getByLabelText(/^Rol/), 'TEACHER');
        await fill(dialog, user, { email: 'pedro@unisimon.edu.co' });
        await user.selectOptions(dialog.getByLabelText(/^Sede/), '1');
        await user.selectOptions(dialog.getByLabelText(/^Facultad/), '2');
        await user.click(dialog.getByRole('button', { name: 'Crear cuenta' }));

        expect(createUser.mutateAsync).toHaveBeenCalledWith(
            expect.objectContaining({ role: 'TEACHER', campusId: 1, facultyId: 2, academicProgramId: null }),
        );
    });

    it('un correo repetido se muestra junto al campo', async () => {
        const user = userEvent.setup();
        createUser.mutateAsync.mockRejectedValue(apiError('El correo marta@empresa.com ya pertenece a otro usuario.', 409));
        const dialog = await openDialog(user);

        await fill(dialog, user, { email: 'marta@empresa.com' });
        await user.click(dialog.getByRole('button', { name: 'Crear cuenta' }));

        expect(await dialog.findByText('El correo marta@empresa.com ya pertenece a otro usuario.')).toBeInTheDocument();
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
});

describe('Cátedras aprobadas', () => {
    const openApprovals = async (table) => {
        await userEvent.click(
            within(row(table, 'Ana María Pérez')).getByRole('button', { name: 'Acciones para Ana María Pérez' }),
        );
        await userEvent.click(await screen.findByRole('menuitem', { name: 'Cátedras aprobadas' }));
        return within(await screen.findByRole('dialog'));
    };

    it('la gestión marca INNPRENDE I como aprobada a mano', async () => {
        const table = renderAs(carla, 'MACONDOLAB');
        const dialog = await openApprovals(table);

        expect(dialog.getAllByText('Sin aprobar')).toHaveLength(2);
        await userEvent.click(dialog.getAllByRole('button', { name: /Marcar como aprobada/ })[0]);

        await waitFor(() => expect(saveApproval.mutateAsync).toHaveBeenCalledWith({ track: 'INNPRENDE_I' }));
        expect(useTrackApprovals).toHaveBeenCalledWith(ana.id);
    });

    it('una aprobación existente dice de dónde viene y se puede quitar', async () => {
        useTrackApprovals.mockReturnValue({
            data: [
                {
                    id: 3,
                    userId: 1,
                    track: 'INNPRENDE_I',
                    projectId: 10,
                    projectTitle: 'BioSensor',
                    approvedBy: 'Carlos Mendoza',
                },
            ],
            isPending: false,
            error: null,
            refetch: vi.fn(),
        });
        const table = renderAs(carla, 'MACONDOLAB');
        const dialog = await openApprovals(table);

        expect(dialog.getByText(/Por el proyecto "BioSensor" · Carlos Mendoza/)).toBeInTheDocument();
        await userEvent.click(dialog.getByRole('button', { name: 'Quitar la aprobación de INNPRENDE I' }));

        await waitFor(() => expect(saveApproval.mutateAsync).toHaveBeenCalledWith({ id: 3 }));
    });

    it('solo los estudiantes tienen cátedras que aprobar', async () => {
        const table = renderAs(luis, 'ADMIN');

        await userEvent.click(within(row(table, 'Carla Díaz')).getByRole('button', { name: 'Acciones para Carla Díaz' }));

        expect(screen.queryByRole('menuitem', { name: 'Cátedras aprobadas' })).not.toBeInTheDocument();
    });
});
