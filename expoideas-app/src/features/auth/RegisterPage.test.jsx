import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ROUTES } from '@/lib/routes';
import { apiError } from '@/test/utils';
import RegisterPage from './RegisterPage';
import { authApi } from './api';

vi.mock('./api', () => ({ authApi: { register: vi.fn() } }));

/** El inicio de sesión muestra el mensaje con el que llega desde el registro. */
function FakeLogin() {
    const { state } = useLocation();
    return (
        <p>
            {state?.message} {state?.email}
        </p>
    );
}

function renderRegister() {
    render(
        <MemoryRouter initialEntries={[ROUTES.REGISTER]}>
            <Routes>
                <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
                <Route path={ROUTES.LOGIN} element={<FakeLogin />} />
            </Routes>
        </MemoryRouter>,
    );
}

async function fillIn(user, { email = 'ana.perez@unisimon.edu.co', password = 'Segura#2026', consent = true } = {}) {
    await user.type(screen.getByLabelText(/^Correo institucional/), email);
    await user.type(screen.getByLabelText(/^Contraseña/), password);
    await user.type(screen.getByLabelText(/^Confirmar contraseña/), password);
    if (consent) await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /Crear cuenta/ }));
}

describe('Registro mínimo', () => {
    it('pide solo correo y contraseña: el nombre y la adscripción van al primer ingreso', () => {
        renderRegister();

        expect(screen.getByLabelText(/^Correo institucional/)).toBeInTheDocument();
        expect(screen.queryByLabelText(/Nombres/)).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/Apellidos/)).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/Sede/)).not.toBeInTheDocument();
        expect(screen.queryByLabelText(/Facultad/)).not.toBeInTheDocument();
        expect(screen.getByText(/Tu nombre y tu facultad los completas al entrar/)).toBeInTheDocument();
    });

    it('crea la cuenta con correo, contraseña y autorización, y lleva al inicio de sesión', async () => {
        const user = userEvent.setup();
        authApi.register.mockResolvedValue({ id: 9, role: 'STUDENT', pendingSteps: ['COMPLETE_PROFILE'] });
        renderRegister();

        await fillIn(user);

        expect(authApi.register).toHaveBeenCalledWith({
            email: 'ana.perez@unisimon.edu.co',
            password: 'Segura#2026',
            dataConsent: true,
        });
        expect(await screen.findByText(/Tu cuenta fue creada/)).toHaveTextContent('ana.perez@unisimon.edu.co');
    });

    it('acepta el dominio institucional en mayúsculas', async () => {
        const user = userEvent.setup();
        authApi.register.mockResolvedValue({});
        renderRegister();

        await fillIn(user, { email: 'Ana.Perez@UNISIMON.EDU.CO' });

        expect(authApi.register).toHaveBeenCalledWith(expect.objectContaining({ email: 'Ana.Perez@UNISIMON.EDU.CO' }));
    });

    it('rechaza un correo que no es institucional y exige la autorización', async () => {
        const user = userEvent.setup();
        renderRegister();

        await fillIn(user, { email: 'ana@gmail.com', consent: false });

        expect(await screen.findByText(/Usa tu correo institucional/)).toBeInTheDocument();
        expect(screen.getByText('Debes autorizar el tratamiento de tus datos para crear la cuenta')).toBeInTheDocument();
        expect(authApi.register).not.toHaveBeenCalled();
    });

    it('un correo ya registrado se muestra en su campo', async () => {
        const user = userEvent.setup();
        authApi.register.mockRejectedValue(apiError('El correo institucional ya está registrado.', 409));
        renderRegister();

        await fillIn(user);

        expect(await screen.findByText('El correo institucional ya está registrado.')).toBeInTheDocument();
        expect(screen.getByLabelText(/^Correo institucional/)).toHaveFocus();
    });
});
