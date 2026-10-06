import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ROUTES } from '@/lib/routes';
import { apiError } from '@/test/utils';
import RecoverPasswordPage from './RecoverPasswordPage';
import ResetPasswordPage from './ResetPasswordPage';
import VerifyEmailPage from './VerifyEmailPage';
import { authApi } from './api';
import { linkToken } from './linkToken';
import { useAuth } from './useAuth';

vi.mock('./useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('./api', () => ({
    authApi: { verifyEmail: vi.fn(), requestPasswordRecovery: vi.fn(), resetPassword: vi.fn() },
}));

/** La pantalla de inicio de sesión, que muestra el mensaje con el que llegan a ella. */
function LoginStub() {
    return <p>Inicia sesión: {useLocation().state?.message}</p>;
}

function renderAt(entry, page) {
    render(
        <MemoryRouter initialEntries={[entry]}>
            <Routes>
                <Route path={ROUTES.VERIFY_EMAIL} element={page} />
                <Route path={ROUTES.RECOVER_PASSWORD} element={page} />
                <Route path={ROUTES.RESET_PASSWORD} element={page} />
                <Route path={ROUTES.LOGIN} element={<LoginStub />} />
                <Route path={ROUTES.ONBOARDING} element={<p>Pantalla de primer ingreso</p>} />
                <Route path={ROUTES.HOME} element={<p>Inicio</p>} />
            </Routes>
        </MemoryRouter>,
    );
}

const completeStep = vi.fn();

beforeEach(() => {
    vi.clearAllMocks();
    useAuth.mockReturnValue({ token: null, role: null, pendingSteps: [], completeStep });
});

describe('El token de un enlace', () => {
    it('se lee del fragmento de la dirección', () => {
        expect(linkToken('#token=abc-123_XYZ')).toBe('abc-123_XYZ');
        expect(linkToken('token=abc')).toBe('abc');
        expect(linkToken('')).toBeNull();
        expect(linkToken('#otra=cosa')).toBeNull();
    });
});

describe('Verificar el correo', () => {
    it('canjea el enlace una sola vez y, sin sesión, invita a iniciarla', async () => {
        authApi.verifyEmail.mockResolvedValue(null);
        renderAt(`${ROUTES.VERIFY_EMAIL}#token=abc`, <VerifyEmailPage />);

        expect(await screen.findByText('Tu correo quedó verificado')).toBeInTheDocument();
        expect(authApi.verifyEmail).toHaveBeenCalledTimes(1);
        expect(authApi.verifyEmail).toHaveBeenCalledWith('abc');
        expect(screen.getByRole('link', { name: /Iniciar sesión/ })).toHaveAttribute('href', ROUTES.LOGIN);
    });

    it('con la sesión abierta quita el paso pendiente y sigue con el primer ingreso', async () => {
        useAuth.mockReturnValue({ token: 'jwt', role: 'STUDENT', pendingSteps: ['COMPLETE_PROFILE'], completeStep });
        authApi.verifyEmail.mockResolvedValue(null);
        renderAt(`${ROUTES.VERIFY_EMAIL}#token=abc`, <VerifyEmailPage />);

        expect(await screen.findByText('Tu correo quedó verificado')).toBeInTheDocument();
        expect(completeStep).toHaveBeenCalledWith('VERIFY_EMAIL');
        expect(screen.getByRole('link', { name: /Continuar/ })).toHaveAttribute('href', ROUTES.ONBOARDING);
    });

    it('un enlace usado o vencido lo dice y no marca nada como verificado', async () => {
        authApi.verifyEmail.mockRejectedValue(apiError('El enlace no es válido o ya venció. Pide uno nuevo.', 409));
        renderAt(`${ROUTES.VERIFY_EMAIL}#token=viejo`, <VerifyEmailPage />);

        expect(await screen.findByText('El enlace no es válido o ya venció. Pide uno nuevo.')).toBeInTheDocument();
        expect(completeStep).not.toHaveBeenCalled();
    });

    it('sin token en la dirección no llama a la API', () => {
        renderAt(ROUTES.VERIFY_EMAIL, <VerifyEmailPage />);

        expect(screen.getByText(/El enlace está incompleto/)).toBeInTheDocument();
        expect(authApi.verifyEmail).not.toHaveBeenCalled();
    });
});

describe('Recuperar la contraseña', () => {
    it('envía el correo y responde lo mismo exista o no la cuenta', async () => {
        const user = userEvent.setup();
        authApi.requestPasswordRecovery.mockResolvedValue(null);
        renderAt(ROUTES.RECOVER_PASSWORD, <RecoverPasswordPage />);

        await user.type(screen.getByLabelText(/^Correo/), 'ana@unisimon.edu.co');
        await user.click(screen.getByRole('button', { name: /Enviar el enlace/ }));

        expect(authApi.requestPasswordRecovery).toHaveBeenCalledWith('ana@unisimon.edu.co');
        expect(await screen.findByText(/Si hay una cuenta con ana@unisimon.edu.co/)).toBeInTheDocument();
    });

    it('si la plataforma no envía correos, dice a quién acudir', async () => {
        const user = userEvent.setup();
        authApi.requestPasswordRecovery.mockRejectedValue(
            apiError('La recuperación por correo todavía no está disponible.', 409),
        );
        renderAt(ROUTES.RECOVER_PASSWORD, <RecoverPasswordPage />);

        await user.type(screen.getByLabelText(/^Correo/), 'ana@unisimon.edu.co');
        await user.click(screen.getByRole('button', { name: /Enviar el enlace/ }));

        expect(await screen.findByText('La recuperación por correo todavía no está disponible.')).toBeInTheDocument();
    });

    it('no envía nada si lo escrito no es un correo', async () => {
        const user = userEvent.setup();
        renderAt(ROUTES.RECOVER_PASSWORD, <RecoverPasswordPage />);

        await user.type(screen.getByLabelText(/^Correo/), 'no-es-un-correo');
        await user.click(screen.getByRole('button', { name: /Enviar el enlace/ }));

        expect(await screen.findByText('Ingresa un correo válido')).toBeInTheDocument();
        expect(authApi.requestPasswordRecovery).not.toHaveBeenCalled();
    });
});

describe('Poner la contraseña nueva', () => {
    const fill = async (user, password, confirmation = password) => {
        await user.type(screen.getByLabelText(/^Nueva contraseña/), password);
        await user.type(screen.getByLabelText(/^Confirmar nueva contraseña/), confirmation);
        await user.click(screen.getByRole('button', { name: /Guardar la contraseña/ }));
    };

    it('envía el token del enlace con la contraseña y lleva a iniciar sesión', async () => {
        const user = userEvent.setup();
        authApi.resetPassword.mockResolvedValue(null);
        renderAt(`${ROUTES.RESET_PASSWORD}#token=abc`, <ResetPasswordPage />);

        await fill(user, 'Recuperada#2026');

        expect(authApi.resetPassword).toHaveBeenCalledWith({
            token: 'abc',
            newPassword: 'Recuperada#2026',
            confirmPassword: 'Recuperada#2026',
        });
        expect(await screen.findByText(/Tu contraseña se cambió/)).toBeInTheDocument();
    });

    it('con un enlace vencido ofrece pedir otro', async () => {
        const user = userEvent.setup();
        authApi.resetPassword.mockRejectedValue(apiError('El enlace no es válido o ya venció. Pide uno nuevo.', 409));
        renderAt(`${ROUTES.RESET_PASSWORD}#token=viejo`, <ResetPasswordPage />);

        await fill(user, 'Recuperada#2026');

        expect(await screen.findByText('El enlace no es válido o ya venció. Pide uno nuevo.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Pedir un enlace nuevo' })).toHaveAttribute('href', ROUTES.RECOVER_PASSWORD);
    });

    it('no envía una contraseña que no se confirma igual', async () => {
        const user = userEvent.setup();
        renderAt(`${ROUTES.RESET_PASSWORD}#token=abc`, <ResetPasswordPage />);

        await fill(user, 'Recuperada#2026', 'Distinta#2026');

        expect(await screen.findByText('Las contraseñas no coinciden')).toBeInTheDocument();
        expect(authApi.resetPassword).not.toHaveBeenCalled();
    });

    it('sin token en la dirección no muestra el formulario', () => {
        renderAt(ROUTES.RESET_PASSWORD, <ResetPasswordPage />);

        expect(screen.getByText('El enlace está incompleto')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Guardar la contraseña/ })).not.toBeInTheDocument();
    });
});
