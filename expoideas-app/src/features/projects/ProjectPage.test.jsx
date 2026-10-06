import { beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useAuth } from '@/features/auth/useAuth';
import { apiError, renderWithProviders, sessionFor } from '@/test/utils';
import { project } from '@/test/fixtures';
import ProjectPage from './ProjectPage';
import { useProjectPresentation } from '@/features/presentations/queries';
import { useJuryProjects } from '@/features/jury/queries';
import { useDeleteProject, useInviteMember, useProject, useRemoveMember, useSetResult } from './queries';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/presentations/queries', () => ({ useProjectPresentation: vi.fn() }));
vi.mock('@/features/jury/JurorsPanel', () => ({ JurorsPanel: () => <section>Panel de jurados</section> }));
vi.mock('@/features/jury/queries', () => ({ useJuryProjects: vi.fn() }));
vi.mock('@/features/evaluations/ProjectEvaluations', () => ({
    ProjectEvaluations: () => <section>Panel de evaluación</section>,
}));
vi.mock('@/features/evaluations/MyGrade', () => ({ MyGrade: () => <section>Tu nota publicada</section> }));
vi.mock('./queries', () => ({
    useProject: vi.fn(),
    useInviteMember: vi.fn(),
    useRemoveMember: vi.fn(),
    useDeleteProject: vi.fn(),
    useSaveProject: vi.fn(),
    useTeachers: vi.fn(),
    useSetResult: vi.fn(),
}));

const navigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
    ...(await importOriginal()),
    useParams: () => ({ id: '10' }),
    useNavigate: () => navigate,
}));

const camilo = {
    userId: 2,
    fullName: 'Camilo Montes',
    email: 'camilo@unisimon.edu.co',
    teamRole: 'MEMBER',
    status: 'ACCEPTED',
};

const withTeam = { ...project, members: [...project.members, camilo] };

let invite;
let removeMember;
let deleteProject;
let setResult;

const renderPage = (data = project, { role = 'STUDENT', userId = 1 } = {}) => {
    useAuth.mockReturnValue(sessionFor({ role, user: { id: userId } }));
    useProject.mockReturnValue({ data, isPending: false, error: null, refetch: vi.fn() });
    renderWithProviders(<ProjectPage />);
};

beforeEach(() => {
    invite = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    removeMember = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    deleteProject = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    setResult = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    useProjectPresentation.mockReturnValue({ data: null, isPending: false, error: null });
    useJuryProjects.mockReturnValue({ data: [] });
    useInviteMember.mockReturnValue(invite);
    useRemoveMember.mockReturnValue(removeMember);
    useDeleteProject.mockReturnValue(deleteProject);
    useSetResult.mockReturnValue(setResult);
});

describe('El proyecto y su equipo', () => {
    it('muestra los datos, el equipo y el tamaño permitido', () => {
        renderPage(withTeam);

        expect(screen.getByRole('heading', { level: 1, name: 'BioSensor' })).toBeInTheDocument();
        expect(screen.getByText('Agroindustria y alimentos')).toBeInTheDocument();
        expect(screen.getByText('Ana María Pérez')).toBeInTheDocument();
        expect(screen.getByText('Líder')).toBeInTheDocument();
        expect(screen.getByText('2 de 5 integrantes · mínimo 2')).toBeInTheDocument();
    });

    it('una invitación sin responder se ve como enviada', () => {
        renderPage({ ...project, members: [...project.members, { ...camilo, status: 'INVITED' }] });

        expect(screen.getByText('Invitación enviada')).toBeInTheDocument();
        // Todavía no cuenta como integrante aceptado.
        expect(screen.getByText('1 de 5 integrantes · mínimo 2')).toBeInTheDocument();
    });

    it('con las inscripciones cerradas no hay acciones', () => {
        renderPage({ ...withTeam, registrationOpen: false });

        expect(screen.queryByRole('button', { name: /Editar/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Invitar/ })).not.toBeInTheDocument();
        expect(screen.getByText(/ya cerraron/)).toBeInTheDocument();
    });
});

describe('El líder gestiona el equipo', () => {
    it('invita por correo institucional', async () => {
        renderPage();

        await userEvent.type(screen.getByLabelText(/Invitar a un compañero/), 'camilo@unisimon.edu.co');
        await userEvent.click(screen.getByRole('button', { name: /Invitar/ }));

        await waitFor(() => expect(invite.mutateAsync).toHaveBeenCalledWith('camilo@unisimon.edu.co'));
    });

    it('rechaza un correo que no es institucional', async () => {
        renderPage();

        await userEvent.type(screen.getByLabelText(/Invitar a un compañero/), 'camilo@gmail.com');
        await userEvent.click(screen.getByRole('button', { name: /Invitar/ }));

        expect(await screen.findByText(/correo institucional de tu compañero/)).toBeInTheDocument();
        expect(invite.mutateAsync).not.toHaveBeenCalled();
    });

    it('muestra en el campo el error que devuelve la API', async () => {
        invite.mutateAsync.mockRejectedValue(
            apiError('Datos inválidos', 400, { fields: { email: 'No hay una cuenta registrada con ese correo' } }),
        );
        renderPage();

        await userEvent.type(screen.getByLabelText(/Invitar a un compañero/), 'nadie@unisimon.edu.co');
        await userEvent.click(screen.getByRole('button', { name: /Invitar/ }));

        expect(await screen.findByText('No hay una cuenta registrada con ese correo')).toBeInTheDocument();
    });

    it('quita a un integrante después de confirmar', async () => {
        renderPage(withTeam);

        await userEvent.click(screen.getByRole('button', { name: 'Quitar a Camilo Montes' }));
        const dialog = within(await screen.findByRole('alertdialog'));
        await userEvent.click(dialog.getByRole('button', { name: 'Quitar' }));

        await waitFor(() => expect(removeMember.mutateAsync).toHaveBeenCalledWith(2));
    });

    it('elimina la inscripción y vuelve a la lista', async () => {
        renderPage();

        await userEvent.click(screen.getByRole('button', { name: /Eliminar/ }));
        const dialog = within(await screen.findByRole('alertdialog'));
        await userEvent.click(dialog.getByRole('button', { name: 'Eliminar' }));

        await waitFor(() => expect(deleteProject.mutateAsync).toHaveBeenCalledWith(10));
        expect(navigate).toHaveBeenCalledWith('/mis-proyectos');
    });

    it('el líder no puede salirse de su propio equipo', () => {
        renderPage(withTeam);

        expect(screen.queryByRole('button', { name: 'Salir del equipo' })).not.toBeInTheDocument();
    });
});

describe('Un integrante que no es líder', () => {
    it('no edita ni invita, pero puede salirse', async () => {
        renderPage(withTeam, { userId: 2 });

        expect(screen.queryByRole('button', { name: /Editar/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Invitar/ })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Salir del equipo' }));
        const dialog = within(await screen.findByRole('alertdialog'));
        await userEvent.click(dialog.getByRole('button', { name: 'Quitar' }));

        await waitFor(() => expect(removeMember.mutateAsync).toHaveBeenCalledWith(2));
        expect(navigate).toHaveBeenCalledWith('/mis-proyectos');
    });
});

describe('El resultado del proyecto', () => {
    const closed = { ...project, registrationOpen: false, submissionOpen: false };

    it('con las entregas abiertas nadie lo registra todavía', () => {
        renderPage(project, { role: 'TEACHER', userId: 7 });

        expect(screen.queryByLabelText('Resultado del proyecto')).not.toBeInTheDocument();
        expect(screen.queryByText('Resultado')).not.toBeInTheDocument();
    });

    it('el profesor del grupo lo registra cuando cierran las entregas', async () => {
        const user = userEvent.setup();
        renderPage(closed, { role: 'TEACHER', userId: 7 });

        expect(screen.getByText('Todavía sin registrar.')).toBeInTheDocument();
        await user.selectOptions(screen.getByLabelText('Resultado del proyecto'), 'APPROVED');
        await user.click(screen.getByRole('button', { name: 'Guardar resultado' }));

        await waitFor(() => expect(setResult.mutateAsync).toHaveBeenCalledWith('APPROVED'));
    });

    it('otro profesor solo lo ve', () => {
        renderPage({ ...closed, result: 'NOT_APPROVED' }, { role: 'TEACHER', userId: 8 });

        expect(screen.getByText('No aprobado')).toBeInTheDocument();
        expect(screen.queryByLabelText('Resultado del proyecto')).not.toBeInTheDocument();
    });

    it('la gestión puede cambiarlo, y el equipo lo ve como insignia', () => {
        renderPage({ ...closed, result: 'APPROVED' }, { role: 'MACONDOLAB', userId: 9 });
        // «Aprobado» está en la insignia y en la opción del select.
        expect(screen.getAllByText('Aprobado')).toHaveLength(2);
        expect(screen.getByLabelText('Resultado del proyecto')).toHaveValue('APPROVED');
        expect(screen.getByRole('button', { name: 'Guardar resultado' })).toBeDisabled();
    });

    it('el equipo ve el resultado sin poder tocarlo', () => {
        renderPage({ ...closed, result: 'APPROVED' });

        expect(screen.getByText('Aprobado')).toBeInTheDocument();
        expect(screen.queryByLabelText('Resultado del proyecto')).not.toBeInTheDocument();
    });
});

describe('La sustentación', () => {
    it('aparece en la ficha cuando la gestión ya la programó', () => {
        useProjectPresentation.mockReturnValue({
            data: {
                id: 3,
                projectId: 10,
                startsAt: '2026-11-20T09:30:00',
                place: 'Auditorio Jorge Artel',
                notes: 'Llegar antes.',
            },
            isPending: false,
            error: null,
        });
        renderPage();

        expect(screen.getByText('Sustentación')).toBeInTheDocument();
        expect(screen.getByText(/20 de noviembre de 2026/)).toBeInTheDocument();
        expect(screen.getByText('Auditorio Jorge Artel')).toBeInTheDocument();
        expect(screen.getByText('Llegar antes.')).toBeInTheDocument();
    });

    it('no ocupa espacio mientras no haya cita', () => {
        renderPage();

        expect(screen.queryByText('Sustentación')).not.toBeInTheDocument();
    });
});

describe('Los jurados', () => {
    it('la gestión ve el panel para asignarlos', () => {
        renderPage(project, { role: 'MACONDOLAB', userId: 9 });

        expect(screen.getByText('Panel de jurados')).toBeInTheDocument();
    });

    it('el equipo y el profesor no lo ven', () => {
        renderPage(project, { role: 'TEACHER', userId: 7 });

        expect(screen.queryByText('Panel de jurados')).not.toBeInTheDocument();
    });

    it('la evaluación la ven el profesor del grupo y la gestión, no el equipo ni un jurado', () => {
        renderPage(project, { role: 'TEACHER', userId: 7 });
        expect(screen.getByText('Panel de evaluación')).toBeInTheDocument();
        screen.getByText('Panel de evaluación').closest('main, body');

        renderPage(project, { role: 'MACONDOLAB', userId: 9 });
        expect(screen.getAllByText('Panel de evaluación')).toHaveLength(2);
    });

    it('la nota publicada es para el equipo aceptado; una invitación sin responder no la ve', () => {
        renderPage(project, { role: 'STUDENT', userId: 1 });
        expect(screen.getByText('Tu nota publicada')).toBeInTheDocument();
    });

    it('quien solo está invitado ve la ficha, pero los entregables los ve cuando acepte', () => {
        const hint = 'Los verás cuando aceptes la invitación, en Mis proyectos.';
        renderPage({ ...project, members: [...project.members, { ...camilo, status: 'INVITED' }] }, { userId: 2 });
        expect(screen.getByRole('heading', { level: 1, name: 'BioSensor' })).toBeInTheDocument();
        expect(screen.getByText(hint)).toBeInTheDocument();
        cleanup();

        renderPage(withTeam, { userId: 2 });
        expect(screen.queryByText(hint)).not.toBeInTheDocument();
    });

    it('el profesor y la gestión no llevan el panel del equipo', () => {
        renderPage(project, { role: 'TEACHER', userId: 7 });
        expect(screen.queryByText('Tu nota publicada')).not.toBeInTheDocument();
    });

    it('ni el equipo ni un jurado ven la evaluación', () => {
        renderPage(project, { role: 'STUDENT', userId: 1 });
        expect(screen.queryByText('Panel de evaluación')).not.toBeInTheDocument();

        useJuryProjects.mockReturnValue({ data: [project] });
        renderPage(project, { role: 'JUDGE', userId: 8 });
        expect(screen.queryByText('Panel de evaluación')).not.toBeInTheDocument();
    });

    it('un jurado vuelve a Evaluar desde la ficha', () => {
        renderPage(project, { role: 'JUDGE', userId: 8 });

        expect(screen.getByRole('link', { name: /Evaluar/ })).toHaveAttribute('href', '/jurado/proyectos');
    });

    it('el jurado asignado tiene el botón para calificar; los demás, no', () => {
        useJuryProjects.mockReturnValue({ data: [project] });
        renderPage(project, { role: 'JUDGE', userId: 8 });

        expect(screen.getByRole('link', { name: 'Calificar' })).toHaveAttribute('href', '/jurado/proyectos/10/calificar');
    });

    it('con la ficha en pantalla, un fallo pasajero al volver a consultarla no la quita', () => {
        useAuth.mockReturnValue(sessionFor({ role: 'STUDENT', user: { id: 1 } }));
        const refresh = (error) => useProject.mockReturnValue({ data: project, isPending: false, error, refetch: vi.fn() });

        refresh(apiError('Error de red. Verifica tu conexión.', 0));
        const { unmount } = renderWithProviders(<ProjectPage />);
        expect(screen.getByRole('heading', { level: 1, name: 'BioSensor' })).toBeInTheDocument();
        expect(screen.queryByText('No pudimos cargar el proyecto')).not.toBeInTheDocument();
        unmount();

        // Si la API dice que ya no puede verlo, sí.
        refresh(apiError('No tienes permiso para ver este proyecto', 403));
        renderWithProviders(<ProjectPage />);
        expect(screen.getByText('No pudimos cargar el proyecto')).toBeInTheDocument();
        expect(screen.queryByRole('heading', { level: 1, name: 'BioSensor' })).not.toBeInTheDocument();
    });

    it('un profesor que no es jurado de este proyecto no lo califica', () => {
        useJuryProjects.mockReturnValue({ data: [{ ...project, id: 99 }] });
        renderPage(project, { role: 'TEACHER', userId: 7 });

        expect(screen.queryByRole('link', { name: 'Calificar' })).not.toBeInTheDocument();
    });
});
