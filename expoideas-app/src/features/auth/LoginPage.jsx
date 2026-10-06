import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight } from 'lucide-react';
import { ROUTES, startRouteFor } from '@/lib/routes';
import { INSTITUTIONAL_DOMAIN, handleFormError } from '@/lib/validation';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { AuthHeading } from '@/components/layout/AuthHeading';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { Alert } from '@/components/ui/feedback';
import { authApi } from './api';
import { loginSchema } from './schemas';
import { useAuth } from './useAuth';

export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { login, sessionExpired } = useAuth();

    // El registro llega aquí con un mensaje y el correo recién creado.
    const registeredMessage = location.state?.message;

    const {
        register,
        handleSubmit,
        setError,
        control,
        formState: { errors, isSubmitting },
    } = useForm({
        resolver: zodResolver(loginSchema),
        defaultValues: { email: location.state?.email ?? '', password: '' },
    });

    const submit = async ({ email, password }) => {
        try {
            const data = await authApi.login({ email, password });
            const pendingSteps = data.pendingSteps ?? [];
            login(
                data.token,
                { id: data.id, email, firstName: data.firstName, lastName: data.lastName, photoId: data.photoId ?? null },
                pendingSteps,
            );

            // Primero el primer ingreso; si no, vuelve a la página protegida de la que venía.
            const from = location.state?.from?.pathname;
            navigate(pendingSteps.length === 0 && from ? from : startRouteFor(data.role, pendingSteps), { replace: true });
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <AuthLayout
            title="Iniciar sesión"
            panelTitle="Las ideas de la Unisimón, a la vista de todos"
            panelText="Donde los proyectos de INNPRENDE I y II se inscriben, se evalúan con jurados y quedan como evidencia."
        >
            <AuthHeading eyebrow="Acceso institucional" title="Bienvenido de nuevo">
                Ingresa con tu correo {INSTITUTIONAL_DOMAIN}.
            </AuthHeading>

            <div className="mt-8 flex flex-col gap-5">
                {registeredMessage && !errors.root && <Alert variant="success" title={registeredMessage} />}
                {sessionExpired && !registeredMessage && !errors.root && (
                    <Alert title="Tu sesión se cerró">Vuelve a entrar para seguir donde ibas.</Alert>
                )}
                {errors.root && <Alert variant="error" title={errors.root.message} />}

                <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5">
                    <Field label="Correo institucional" error={errors.email?.message} required>
                        <Input
                            type="email"
                            autoComplete="username"
                            inputMode="email"
                            placeholder={`nombre${INSTITUTIONAL_DOMAIN}`}
                            {...register('email')}
                        />
                    </Field>

                    <Field label="Contraseña" error={errors.password?.message} required>
                        <PasswordInput autoComplete="current-password" {...register('password')} />
                    </Field>

                    <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
                        Iniciar sesión <ArrowRight />
                    </Button>
                </form>

                <p className="text-center text-sm">
                    <Link to={ROUTES.RECOVER_PASSWORD} className="font-semibold text-primary underline-offset-4 hover:underline">
                        ¿Olvidaste tu contraseña?
                    </Link>
                </p>

                <p className="text-center text-sm text-on-surface-variant">
                    ¿Aún no tienes cuenta?{' '}
                    <Link to={ROUTES.REGISTER} className="font-semibold text-primary underline-offset-4 hover:underline">
                        Crea tu cuenta
                    </Link>
                </p>
            </div>
        </AuthLayout>
    );
}
