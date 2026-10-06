import { describe, expect, it, vi } from 'vitest';
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, createTestQueryClient, renderWithProviders, sessionFor } from '@/test/utils';
import { useAuth } from '@/features/auth/useAuth';
import ProfilePage from './ProfilePage';
import { profileApi } from './api';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('./api', () => ({ profileApi: { get: vi.fn(), update: vi.fn(), uploadPhoto: vi.fn(), deletePhoto: vi.fn() } }));
vi.mock('@/features/catalogs/api', () => ({
    CATALOG_PATHS: { campuses: '/campuses', faculties: '/faculties', academicPrograms: '/academic-programs' },
    catalogApi: { list: vi.fn().mockResolvedValue([]) },
}));

const baseProfile = {
    id: 5,
    firstName: 'Marta',
    lastName: 'Ríos',
    email: 'marta@empresa.com',
    createdAt: '2026-09-17T08:00:00',
    photoId: null,
};

/** Etiquetas de la tarjeta de identidad (Correo, Sede, Facultad...). */
const cardTerms = () => screen.getAllByRole('term').map((term) => term.textContent);

/** Valor que muestra la tarjeta para una etiqueta. */
const cardValue = (label) =>
    within(screen.getAllByRole('term').find((term) => term.textContent === label).parentElement).getByRole('definition')
        .textContent;

function renderProfile(profile) {
    useAuth.mockReturnValue(sessionFor({ role: profile.role }));
    profileApi.get.mockResolvedValue(profile);
    renderWithProviders(<ProfilePage />);
}

describe('Mi perfil según el rol', () => {
    it('un jurado no ve nada de adscripción académica', async () => {
        renderProfile({ ...baseProfile, role: 'JUDGE' });

        expect(await screen.findByLabelText(/^Nombres/)).toHaveValue('Marta');
        expect(screen.queryByText('Adscripción académica')).not.toBeInTheDocument();
        expect(screen.queryByText('Completa tu vínculo con la universidad')).not.toBeInTheDocument();
        expect(cardTerms()).toEqual(['Correo institucional', 'Miembro desde']);
        expect(screen.getByText('Jurado')).toBeInTheDocument();
    });

    it('un jurado guarda solo nombre y apellido', async () => {
        const user = userEvent.setup();
        profileApi.update.mockResolvedValue({ ...baseProfile, firstName: 'Marta Lucía', role: 'JUDGE' });
        renderProfile({ ...baseProfile, role: 'JUDGE' });

        const firstName = await screen.findByLabelText(/^Nombres/);
        await user.clear(firstName);
        await user.type(firstName, 'Marta Lucía');
        await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

        expect(profileApi.update).toHaveBeenCalledWith({ firstName: 'Marta Lucía', lastName: 'Ríos' }, expect.anything());
    });

    it('un estudiante sin facultad ve el aviso y la adscripción', async () => {
        renderProfile({ ...baseProfile, email: 'ana@unisimon.edu.co', role: 'STUDENT', facultyId: null });

        expect(await screen.findByText('Completa tu vínculo con la universidad')).toBeInTheDocument();
        expect(screen.getByText('Adscripción académica')).toBeInTheDocument();
        expect(cardTerms()).toEqual(['Correo institucional', 'Sede', 'Facultad', 'Programa académico', 'Miembro desde']);
        expect(cardValue('Facultad')).toBe('Sin asignar');
    });

    it('un docente con adscripción no ve el aviso', async () => {
        renderProfile({
            ...baseProfile,
            email: 'pedro@unisimon.edu.co',
            role: 'TEACHER',
            campusId: 1,
            campus: 'Barranquilla',
            facultyId: 2,
            faculty: 'Ingeniería',
        });

        expect(await screen.findByText('Adscripción académica')).toBeInTheDocument();
        expect(screen.queryByText('Completa tu vínculo con la universidad')).not.toBeInTheDocument();
        expect(cardValue('Facultad')).toBe('Ingeniería');
        expect(cardValue('Programa académico')).toBe('Sin programa');
    });
});

describe('El perfil se vuelve a consultar cada minuto', () => {
    it('si una de esas consultas falla, el formulario a medio llenar sigue ahí', async () => {
        const user = userEvent.setup();
        const queryClient = createTestQueryClient();
        useAuth.mockReturnValue(sessionFor({ role: 'JUDGE' }));
        profileApi.get
            .mockResolvedValueOnce({ ...baseProfile, role: 'JUDGE' })
            .mockRejectedValue(apiError('Error de red. Verifica tu conexión.', 0));
        renderWithProviders(<ProfilePage />, { queryClient });

        await user.type(await screen.findByLabelText(/^Nombres/), ' Lucía');
        await act(async () => {
            await queryClient.refetchQueries({ queryKey: ['profile'] });
            // TanStack Query avisa a la página en la siguiente vuelta: hay que dejarla pasar.
            await new Promise((resolve) => setTimeout(resolve, 0));
        });

        expect(profileApi.get).toHaveBeenCalledTimes(2);
        expect(screen.queryByText('No pudimos cargar tu perfil')).not.toBeInTheDocument();
        expect(screen.getByLabelText(/^Nombres/)).toHaveValue('Marta Lucía');
    });

    it('si falla la primera, sin nada que mostrar, se explica y se puede reintentar', async () => {
        useAuth.mockReturnValue(sessionFor({ role: 'JUDGE' }));
        profileApi.get.mockRejectedValue(apiError('Error de red. Verifica tu conexión.', 0));
        renderWithProviders(<ProfilePage />);

        expect(await screen.findByText('No pudimos cargar tu perfil')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Reintentar/ })).toBeInTheDocument();
    });
});
