import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, renderWithProviders } from '@/test/utils';
import { InviteForm } from './InviteForm';
import { useInvitationCandidates, useInviteMember } from './queries';

vi.mock('./queries', () => ({
    MIN_CANDIDATE_SEARCH: 3,
    useInviteMember: vi.fn(),
    useInvitationCandidates: vi.fn(),
}));

const mauro = {
    id: 21,
    fullName: 'Mauro Peña',
    email: 'm***@unisimon.edu.co',
    campus: 'Barranquilla',
    faculty: 'Ingeniería',
    academicProgram: 'Ingeniería de Sistemas',
};
const maura = {
    id: 22,
    fullName: 'Maura Díaz',
    email: 'm***@unisimon.edu.co',
    campus: 'Cúcuta',
    faculty: 'Derecho',
    academicProgram: null,
};

let invite;

/** Como la API: con el texto con el que se buscó, esos compañeros. */
const renderForm = ({ found = [], searching = false, disabled = false } = {}) => {
    useInvitationCandidates.mockImplementation((projectId, search) => ({
        data: search ? found : undefined,
        isPending: searching || !search,
        error: null,
    }));
    renderWithProviders(<InviteForm projectId={10} disabled={disabled} />);
};

const field = () => screen.getByLabelText(/Invitar a un compañero/);
const lastSearch = () => useInvitationCandidates.mock.calls.at(-1)[1];

beforeEach(() => {
    invite = { mutateAsync: vi.fn().mockResolvedValue({}), isPending: false };
    useInviteMember.mockReturnValue(invite);
});

describe('Invitar buscando por nombre', () => {
    it('muestra a quienes se llaman así, con de dónde son, y lo invita sin escribir su correo', async () => {
        renderForm({ found: [maura, mauro] });

        await userEvent.type(field(), 'Maur');

        const results = within(await screen.findByRole('list', { name: 'Compañeros encontrados' }));
        expect(lastSearch()).toBe('Maur');
        expect(results.getByText('Mauro Peña')).toBeInTheDocument();
        expect(results.getByText('Ingeniería de Sistemas · Barranquilla · m***@unisimon.edu.co')).toBeInTheDocument();
        // Sin programa, se dice la facultad.
        expect(results.getByText('Derecho · Cúcuta · m***@unisimon.edu.co')).toBeInTheDocument();

        await userEvent.click(results.getByRole('button', { name: 'Invitar a Mauro Peña' }));

        await waitFor(() => expect(invite.mutateAsync).toHaveBeenCalledWith({ userId: 21 }));
        // Invitado, el campo queda limpio para el siguiente.
        await waitFor(() => expect(field()).toHaveValue(''));
    });

    it('con menos de tres letras no busca, y lo explica si se intenta invitar así', async () => {
        renderForm({ found: [mauro] });

        await userEvent.type(field(), 'Ma');

        expect(lastSearch()).toBe('');
        expect(screen.queryByRole('list', { name: 'Compañeros encontrados' })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: /Invitar/ }));

        expect(screen.getByRole('alert')).toHaveTextContent('Escribe al menos 3 letras de su nombre, o su correo completo.');
        expect(invite.mutateAsync).not.toHaveBeenCalled();
    });

    it('un nombre no basta para invitar: hay que elegir a la persona', async () => {
        renderForm({ found: [mauro] });

        await userEvent.type(field(), 'Mauro{Enter}');

        expect(screen.getByRole('alert')).toHaveTextContent('Elige a tu compañero en la lista, o escribe su correo completo.');
        expect(invite.mutateAsync).not.toHaveBeenCalled();
    });

    it('si nadie se llama así, sugiere probar con el correo', async () => {
        renderForm({ found: [] });

        await userEvent.type(field(), 'Zacarías');

        expect(await screen.findByText(/Nadie con ese nombre tiene cuenta para invitar/)).toBeInTheDocument();
    });

    it('lo que impide invitar a quien se eligió se muestra en el campo', async () => {
        invite.mutateAsync.mockRejectedValue(
            apiError('Datos inválidos', 400, { fields: { email: 'Esa persona ya tiene un proyecto en esta cátedra' } }),
        );
        renderForm({ found: [mauro] });

        await userEvent.type(field(), 'Mauro');
        await userEvent.click(await screen.findByRole('button', { name: 'Invitar a Mauro Peña' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Esa persona ya tiene un proyecto en esta cátedra');
        // Lo escrito se queda: puede elegir a otra persona de la misma lista.
        expect(field()).toHaveValue('Mauro');
    });

    it('con el equipo completo la lista sale, pero no deja invitar', async () => {
        renderForm({ found: [mauro], disabled: true });

        await userEvent.type(field(), 'Mauro');

        expect(await screen.findByRole('button', { name: 'Invitar a Mauro Peña' })).toBeDisabled();
    });
});

describe('Invitar con el correo', () => {
    it('con una arroba lo escrito es un correo: no se busca y se invita por él', async () => {
        renderForm({ found: [mauro] });

        await userEvent.type(field(), 'camilo@unisimon.edu.co');
        await userEvent.click(screen.getByRole('button', { name: /Invitar/ }));

        expect(lastSearch()).toBe('');
        expect(screen.queryByRole('list', { name: 'Compañeros encontrados' })).not.toBeInTheDocument();
        await waitFor(() => expect(invite.mutateAsync).toHaveBeenCalledWith({ email: 'camilo@unisimon.edu.co' }));
    });
});
