import { createContext, useContext, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ROUTES } from '@/lib/routes';
import { useAffiliationCatalogs } from '@/features/catalogs/queries';
import { useProfile, useUpdateProfile } from '@/features/profile/queries';
import { apiError } from '@/test/utils';
import OnboardingPage from './OnboardingPage';
import { accountApi } from './api';
import { useAuth } from './useAuth';

vi.mock('./useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('./api', () => ({
    accountApi: { changePassword: vi.fn(), giveDataConsent: vi.fn(), resendVerification: vi.fn() },
}));
vi.mock('@/features/profile/queries', () => ({ useUpdateProfile: vi.fn(), useProfile: vi.fn() }));
vi.mock('@/features/catalogs/queries', () => ({ useAffiliationCatalogs: vi.fn() }));

const TestSession = createContext(null);
const renewToken = vi.fn();

/** Sesión con estado real: completar un paso lo quita de la lista, como AuthProvider. */
function Session({ initialPendingSteps, role, user = { fullName: 'Marta Ríos' }, children }) {
    const [pendingSteps, setPendingSteps] = useState(initialPendingSteps);
    const value = {
        token: 'token',
        role,
        user,
        pendingSteps,
        completeStep: (step) => setPendingSteps((steps) => steps.filter((s) => s !== step)),
        logout: vi.fn(),
        renewToken,
    };
    return <TestSession.Provider value={value}>{children}</TestSession.Provider>;
}

function renderOnboarding(pendingSteps, role = 'JUDGE', user = undefined) {
    useAuth.mockImplementation(() => useContext(TestSession));
    render(
        <Session initialPendingSteps={pendingSteps} role={role} user={user}>
            <MemoryRouter initialEntries={[ROUTES.ONBOARDING]}>
                <Routes>
                    <Route path={ROUTES.ONBOARDING} element={<OnboardingPage />} />
                    <Route path={ROUTES.HOME} element={<p>Inicio</p>} />
                    <Route path={ROUTES.USERS} element={<p>Pantalla de usuarios</p>} />
                    <Route path={ROUTES.LOGIN} element={<p>Inicia sesión</p>} />
                </Routes>
            </MemoryRouter>
        </Session>,
    );
}

async function changePassword(user, { current = 'Temporal#2026', next = 'Propia#2026' } = {}) {
    await user.type(screen.getByLabelText(/^Contraseña temporal/), current);
    await user.type(screen.getByLabelText(/^Nueva contraseña/), next);
    await user.type(screen.getByLabelText(/^Confirmar nueva contraseña/), next);
    await user.click(screen.getByRole('button', { name: /Guardar y continuar/ }));
}

const updateProfile = { mutateAsync: vi.fn() };
const profile = { refetch: vi.fn() };

beforeEach(() => {
    updateProfile.mutateAsync.mockReset();
    renewToken.mockReset();
    useUpdateProfile.mockReturnValue(updateProfile);
    profile.refetch.mockReset();
    useProfile.mockReturnValue(profile);
    useAffiliationCatalogs.mockReturnValue({
        campuses: [{ id: 1, name: 'Barranquilla' }],
        faculties: [{ id: 2, name: 'Ingeniería' }],
        programs: [{ id: 5, name: 'Ingeniería de Sistemas', facultyId: 2 }],
        isPending: false,
        error: null,
    });
});

describe('Verificación del correo en el primer ingreso', () => {
    const student = { fullName: '', email: 'ana@unisimon.edu.co' };

    it('es el primer paso y dice a qué correo se envió el enlace', () => {
        renderOnboarding(['VERIFY_EMAIL', 'COMPLETE_PROFILE'], 'STUDENT', student);

        expect(screen.getByText('Primer ingreso · Paso 1 de 2')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Verifica tu correo' })).toBeInTheDocument();
        expect(screen.getByText('Te enviamos un enlace a ana@unisimon.edu.co')).toBeInTheDocument();
    });

    it('no avanza mientras la API siga viendo el correo sin verificar', async () => {
        const user = userEvent.setup();
        profile.refetch.mockResolvedValue({ data: { pendingSteps: ['VERIFY_EMAIL', 'COMPLETE_PROFILE'] } });
        renderOnboarding(['VERIFY_EMAIL', 'COMPLETE_PROFILE'], 'STUDENT', student);

        await user.click(screen.getByRole('button', { name: /Ya abrí el enlace/ }));

        expect(await screen.findByText(/Todavía no vemos tu correo verificado/)).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Verifica tu correo' })).toBeInTheDocument();
    });

    it('al abrir el enlace, aunque sea en otro dispositivo, sigue con el paso siguiente', async () => {
        const user = userEvent.setup();
        profile.refetch.mockResolvedValue({ data: { pendingSteps: ['COMPLETE_PROFILE'] } });
        renderOnboarding(['VERIFY_EMAIL', 'COMPLETE_PROFILE'], 'STUDENT', student);

        await user.click(screen.getByRole('button', { name: /Ya abrí el enlace/ }));

        expect(await screen.findByRole('heading', { name: 'Completa tu perfil' })).toBeInTheDocument();
    });

    it('pide otro enlace y muestra lo que responde la API si es muy pronto', async () => {
        const user = userEvent.setup();
        accountApi.resendVerification.mockResolvedValueOnce(null);
        renderOnboarding(['VERIFY_EMAIL'], 'STUDENT', student);

        await user.click(screen.getByRole('button', { name: 'Enviarme otro enlace' }));
        expect(await screen.findByText(/Te enviamos un enlace nuevo/)).toBeInTheDocument();

        accountApi.resendVerification.mockRejectedValueOnce(apiError('Acabamos de enviarte un enlace.', 409));
        await user.click(screen.getByRole('button', { name: 'Enviarme otro enlace' }));
        expect(await screen.findByText('Acabamos de enviarte un enlace.')).toBeInTheDocument();
    });
});

describe('Primer ingreso', () => {
    it('una cuenta nueva cambia la contraseña, autoriza sus datos y llega al inicio', async () => {
        const user = userEvent.setup();
        accountApi.changePassword.mockResolvedValue({ token: 'token-nuevo' });
        accountApi.giveDataConsent.mockResolvedValue(null);
        renderOnboarding(['CHANGE_PASSWORD', 'DATA_CONSENT']);

        expect(screen.getByText('Primer ingreso · Paso 1 de 2')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Crea tu contraseña' })).toBeInTheDocument();
        await changePassword(user);

        expect(accountApi.changePassword).toHaveBeenCalledWith({
            currentPassword: 'Temporal#2026',
            newPassword: 'Propia#2026',
            confirmPassword: 'Propia#2026',
        });
        expect(await screen.findByText('Primer ingreso · Paso 2 de 2')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Autoriza el tratamiento de tus datos' })).toBeInTheDocument();
        // El cambio cerró la sesión de la contraseña temporal: se sigue con el token que devolvió la API.
        expect(renewToken).toHaveBeenCalledWith('token-nuevo');

        await user.click(screen.getByRole('checkbox'));
        await user.click(screen.getByRole('button', { name: /Aceptar y continuar/ }));

        expect(accountApi.giveDataConsent).toHaveBeenCalled();
        expect(await screen.findByText('Inicio')).toBeInTheDocument();
    });

    it('una contraseña temporal incorrecta se muestra en su campo y no avanza', async () => {
        const user = userEvent.setup();
        accountApi.changePassword.mockRejectedValue(
            apiError('La contraseña actual es incorrecta.', 400, {
                fields: { currentPassword: 'La contraseña actual es incorrecta.' },
            }),
        );
        renderOnboarding(['CHANGE_PASSWORD', 'DATA_CONSENT']);

        await changePassword(user, { current: 'Otra#2026' });

        expect(await screen.findByText('La contraseña actual es incorrecta.')).toBeInTheDocument();
        expect(screen.getByText('Primer ingreso · Paso 1 de 2')).toBeInTheDocument();
    });

    it('la nueva contraseña debe ser distinta de la temporal', async () => {
        const user = userEvent.setup();
        renderOnboarding(['CHANGE_PASSWORD']);

        await changePassword(user, { current: 'Temporal#2026', next: 'Temporal#2026' });

        expect(await screen.findByText('La nueva contraseña debe ser distinta de la actual')).toBeInTheDocument();
        expect(accountApi.changePassword).not.toHaveBeenCalled();
    });

    it('sin marcar la autorización no continúa', async () => {
        const user = userEvent.setup();
        renderOnboarding(['DATA_CONSENT']);

        expect(screen.getByText('Primer ingreso · Paso 1 de 1')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: /Aceptar y continuar/ }));

        expect(
            await screen.findByText('Debes autorizar el tratamiento de tus datos para usar la plataforma'),
        ).toBeInTheDocument();
        expect(accountApi.giveDataConsent).not.toHaveBeenCalled();
    });

    it('MacondoLab termina en Usuarios', async () => {
        const user = userEvent.setup();
        accountApi.giveDataConsent.mockResolvedValue(null);
        renderOnboarding(['DATA_CONSENT'], 'MACONDOLAB');

        await user.click(screen.getByRole('checkbox'));
        await user.click(screen.getByRole('button', { name: /Aceptar y continuar/ }));

        expect(await screen.findByText('Pantalla de usuarios')).toBeInTheDocument();
    });

    it('sin pasos pendientes no se queda en la pantalla', () => {
        renderOnboarding([]);

        expect(screen.getByText('Inicio')).toBeInTheDocument();
    });
});

/** El registro solo pidió correo y contraseña: el nombre y la adscripción se completan aquí. */
describe('Paso «Completa tu perfil»', () => {
    it('un estudiante recién registrado indica nombre, sede, facultad y programa, y llega al inicio', async () => {
        const user = userEvent.setup();
        updateProfile.mutateAsync.mockResolvedValue({ firstName: 'Ana María', lastName: 'Pérez', pendingSteps: [] });
        renderOnboarding(['COMPLETE_PROFILE'], 'STUDENT');

        expect(screen.getByText('Primer ingreso · Paso 1 de 1')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Completa tu perfil' })).toBeInTheDocument();

        await user.type(screen.getByLabelText(/^Nombres/), 'Ana María');
        await user.type(screen.getByLabelText(/^Apellidos/), 'Pérez');
        await user.selectOptions(screen.getByLabelText(/^Sede/), '1');
        await user.selectOptions(screen.getByLabelText(/^Facultad/), '2');
        await user.selectOptions(screen.getByLabelText(/^Programa académico/), '5');
        await user.click(screen.getByRole('button', { name: /Guardar y continuar/ }));

        expect(updateProfile.mutateAsync).toHaveBeenCalledWith({
            firstName: 'Ana María',
            lastName: 'Pérez',
            campusId: 1,
            facultyId: 2,
            academicProgramId: 5,
        });
        expect(await screen.findByText('Inicio')).toBeInTheDocument();
    });

    it('si el listado de la cátedra traía el nombre, viene puesto y se puede corregir', async () => {
        const user = userEvent.setup();
        updateProfile.mutateAsync.mockResolvedValue({ firstName: 'Carlos', lastName: 'Mendoza', pendingSteps: [] });
        renderOnboarding(['COMPLETE_PROFILE'], 'TEACHER', {
            fullName: 'Carlos Mendoza',
            firstName: 'Carlos',
            lastName: 'Mendoza',
        });

        expect(screen.getByLabelText(/^Nombres/)).toHaveValue('Carlos');
        expect(screen.getByLabelText(/^Apellidos/)).toHaveValue('Mendoza');

        await user.clear(screen.getByLabelText(/^Nombres/));
        await user.type(screen.getByLabelText(/^Nombres/), 'Carlos Andrés');
        await user.selectOptions(screen.getByLabelText(/^Sede/), '1');
        await user.selectOptions(screen.getByLabelText(/^Facultad/), '2');
        await user.click(screen.getByRole('button', { name: /Guardar y continuar/ }));

        expect(updateProfile.mutateAsync).toHaveBeenCalledWith(
            expect.objectContaining({ firstName: 'Carlos Andrés', lastName: 'Mendoza', campusId: 1, facultyId: 2 }),
        );
    });

    it('a un estudiante le exige sede y facultad antes de enviar', async () => {
        const user = userEvent.setup();
        renderOnboarding(['COMPLETE_PROFILE'], 'STUDENT');

        await user.type(screen.getByLabelText(/^Nombres/), 'Ana');
        await user.type(screen.getByLabelText(/^Apellidos/), 'Pérez');
        await user.click(screen.getByRole('button', { name: /Guardar y continuar/ }));

        // Por rol: "Selecciona la sede" también es el texto de una opción del select.
        await screen.findAllByRole('alert');
        const errors = screen.getAllByRole('alert').map((error) => error.textContent);
        expect(errors).toEqual(expect.arrayContaining(['Selecciona la sede', 'Selecciona la facultad']));
        expect(updateProfile.mutateAsync).not.toHaveBeenCalled();
    });

    it('a un jurado solo le pide el nombre', async () => {
        const user = userEvent.setup();
        updateProfile.mutateAsync.mockResolvedValue({ firstName: 'Marta', lastName: 'Ríos', pendingSteps: [] });
        renderOnboarding(['COMPLETE_PROFILE'], 'JUDGE');

        expect(screen.queryByLabelText(/^Sede/)).not.toBeInTheDocument();
        await user.type(screen.getByLabelText(/^Nombres/), 'Marta');
        await user.type(screen.getByLabelText(/^Apellidos/), 'Ríos');
        await user.click(screen.getByRole('button', { name: /Guardar y continuar/ }));

        expect(updateProfile.mutateAsync).toHaveBeenCalledWith({ firstName: 'Marta', lastName: 'Ríos' });
        expect(await screen.findByText('Inicio')).toBeInTheDocument();
    });

    it('los errores por campo de la API se muestran en su campo', async () => {
        const user = userEvent.setup();
        updateProfile.mutateAsync.mockRejectedValue(
            apiError('Datos inválidos', 400, {
                fields: { academicProgramId: 'El programa académico no pertenece a la facultad seleccionada.' },
            }),
        );
        renderOnboarding(['COMPLETE_PROFILE'], 'STUDENT');

        await user.type(screen.getByLabelText(/^Nombres/), 'Ana');
        await user.type(screen.getByLabelText(/^Apellidos/), 'Pérez');
        await user.selectOptions(screen.getByLabelText(/^Sede/), '1');
        await user.selectOptions(screen.getByLabelText(/^Facultad/), '2');
        await user.click(screen.getByRole('button', { name: /Guardar y continuar/ }));

        expect(await screen.findByText('El programa académico no pertenece a la facultad seleccionada.')).toBeInTheDocument();
        expect(screen.getByText('Primer ingreso · Paso 1 de 1')).toBeInTheDocument();
    });

    it('viene después de la contraseña y la autorización', () => {
        renderOnboarding(['CHANGE_PASSWORD', 'DATA_CONSENT', 'COMPLETE_PROFILE'], 'STUDENT');

        expect(screen.getByText('Primer ingreso · Paso 1 de 3')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Crea tu contraseña' })).toBeInTheDocument();
    });
});
