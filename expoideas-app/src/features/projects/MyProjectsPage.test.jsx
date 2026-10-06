import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useAuth } from '@/features/auth/useAuth';
import { useEditions } from '@/features/editions/queries';
import { renderWithProviders, sessionFor } from '@/test/utils';
import { openEdition, project } from '@/test/fixtures';
import MyProjectsPage from './MyProjectsPage';
import { useAnswerInvitation, useMyInvitations, useMyProjects } from './queries';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/editions/queries', () => ({ useEditions: vi.fn() }));
vi.mock('./queries', () => ({
    useMyProjects: vi.fn(),
    useMyInvitations: vi.fn(),
    useAnswerInvitation: vi.fn(),
    useSaveProject: vi.fn(),
    useTeachers: vi.fn(),
}));

const invitation = {
    id: 4,
    projectId: 20,
    projectTitle: 'Agua limpia',
    edition: 'Expoideas 2026-2',
    track: 'INNPRENDE_II',
    leader: 'Camilo Montes',
    invitedAt: '2026-11-05T10:00:00',
};

let answer;

const renderPage = ({ projects = [project], invitations = [], editions = [openEdition] } = {}) => {
    useMyProjects.mockReturnValue({ data: projects, isPending: false, error: null, refetch: vi.fn() });
    useMyInvitations.mockReturnValue({ data: invitations, isPending: false, error: null });
    useEditions.mockReturnValue({ data: editions, isPending: false, error: null });
    renderWithProviders(<MyProjectsPage />);
};

beforeEach(() => {
    useAuth.mockReturnValue(sessionFor({ role: 'STUDENT', user: { id: 1 } }));
    answer = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    useAnswerInvitation.mockReturnValue(answer);
});

describe('Mis proyectos', () => {
    it('muestra el proyecto con su cátedra, su equipo y su docente', () => {
        renderPage();

        expect(screen.getByRole('heading', { level: 2, name: 'BioSensor' })).toBeInTheDocument();
        expect(screen.getByText('INNPRENDE I · Despegue')).toBeInTheDocument();
        expect(screen.getByText('Carlos Mendoza')).toBeInTheDocument();
        expect(screen.getByText('1 de 5 integrantes')).toBeInTheDocument();
    });

    it('sin proyectos invita a inscribir uno mientras haya inscripciones abiertas', () => {
        renderPage({ projects: [] });

        expect(screen.getByText('Todavía no tienes proyectos')).toBeInTheDocument();
        expect(screen.getAllByRole('button', { name: /Inscribir proyecto/ }).length).toBeGreaterThan(0);
    });

    it('sin inscripciones abiertas no ofrece inscribir', () => {
        renderPage({ projects: [], editions: [{ ...openEdition, registrationOpen: false }] });

        expect(screen.queryByRole('button', { name: /Inscribir proyecto/ })).not.toBeInTheDocument();
        expect(screen.getByText(/Cuando se abran las inscripciones/)).toBeInTheDocument();
    });

    it('un proyecto con la invitación sin responder no cuenta como propio', () => {
        const invited = {
            ...project,
            id: 20,
            title: 'Agua limpia',
            members: [
                { userId: 9, fullName: 'Camilo Montes', email: 'camilo@unisimon.edu.co', teamRole: 'LEADER', status: 'ACCEPTED' },
                { userId: 1, fullName: 'Ana Pérez', email: 'ana@unisimon.edu.co', teamRole: 'MEMBER', status: 'INVITED' },
            ],
        };
        renderPage({ projects: [invited], invitations: [invitation] });

        expect(screen.queryByRole('heading', { level: 2, name: 'Agua limpia' })).not.toBeInTheDocument();
        expect(screen.getByText('Aún no estás en ningún equipo')).toBeInTheDocument();
    });
});

describe('Invitaciones', () => {
    it('muestra quién invita y a qué proyecto', () => {
        renderPage({ invitations: [invitation] });

        const card = screen.getByText('Agua limpia').closest('div[data-slot="card"]');
        expect(within(card).getByText(/Camilo Montes te invitó/)).toBeInTheDocument();
        expect(within(card).getByText(/INNPRENDE II · Aterrizaje/)).toBeInTheDocument();
    });

    it('con una invitación sin responder no dice que no hay nada ni repite el botón', () => {
        renderPage({ projects: [], invitations: [invitation] });

        expect(screen.getByText('Aún no estás en ningún equipo')).toBeInTheDocument();
        expect(screen.queryByText('Todavía no tienes proyectos')).not.toBeInTheDocument();
        expect(screen.getAllByRole('button', { name: /Inscribir proyecto/ })).toHaveLength(1);
    });

    it('aceptar entra al equipo', async () => {
        renderPage({ invitations: [invitation] });

        await userEvent.click(screen.getByRole('button', { name: 'Aceptar' }));

        expect(answer.mutateAsync).toHaveBeenCalledWith({ id: 4, accept: true });
    });

    it('rechazar descarta la invitación', async () => {
        renderPage({ invitations: [invitation] });

        await userEvent.click(screen.getByRole('button', { name: 'Rechazar' }));

        expect(answer.mutateAsync).toHaveBeenCalledWith({ id: 4, accept: false });
    });
});
