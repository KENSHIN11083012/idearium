import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight } from 'lucide-react';
import { ROUTES } from '@/lib/routes';
import { INSTITUTIONAL_DOMAIN, handleFormError } from '@/lib/validation';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { AuthHeading } from '@/components/layout/AuthHeading';
import { DataConsentField } from '@/components/forms/DataConsentField';
import { PasswordFields } from '@/components/forms/PasswordFields';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/feedback';
import { authApi } from './api';
import { registrationSchema } from './schemas';

/**
 * Registro mínimo: correo institucional, contraseña y autorización de datos. El
 * nombre y el vínculo con la universidad se piden en el primer ingreso.
 */
export default function RegisterPage() {
    const navigate = useNavigate();
    const {
        register,
        control,
        handleSubmit,
        setError,
        formState: { errors, isSubmitting },
    } = useForm({
        resolver: zodResolver(registrationSchema),
        mode: 'onTouched',
        defaultValues: { email: '', password: '', confirmPassword: '', dataConsent: false },
    });

    const submit = async ({ confirmPassword: _, ...values }) => {
        try {
            const created = await authApi.register(values);
            // Si la plataforma envía correos, la cuenta no sirve hasta abrir el enlace de verificación.
            const message = created?.pendingSteps?.includes('VERIFY_EMAIL')
                ? 'Tu cuenta fue creada. Te enviamos un enlace a tu correo para verificarla.'
                : 'Tu cuenta fue creada. Ya puedes iniciar sesión.';
            navigate(ROUTES.LOGIN, { state: { message, email: values.email } });
        } catch (error) {
            if (error.status === 409) {
                setError('email', { type: 'server', message: error.message }, { shouldFocus: true });
            } else {
                handleFormError(error, setError, control);
            }
        }
    };

    return (
        <AuthLayout
            title="Crear cuenta"
            panelTitle="Tu idea merece más que una presentación de clase"
            panelText="Crea tu cuenta con el correo institucional para inscribir tus proyectos, subir tus entregables y consultar tu evaluación."
        >
            <AuthHeading eyebrow="Nueva cuenta" title="Crea tu cuenta">
                Solo para la comunidad de la Universidad Simón Bolívar. Tu nombre y tu facultad los completas al entrar.
            </AuthHeading>

            <form onSubmit={handleSubmit(submit)} noValidate className="mt-8 flex flex-col gap-5">
                {errors.root && <Alert variant="error" title={errors.root.message} />}

                <Field
                    label="Correo institucional"
                    error={errors.email?.message}
                    hint={`Debe terminar en ${INSTITUTIONAL_DOMAIN}`}
                    required
                >
                    <Input
                        type="email"
                        autoComplete="email"
                        inputMode="email"
                        placeholder={`nombre${INSTITUTIONAL_DOMAIN}`}
                        {...register('email')}
                    />
                </Field>

                <PasswordFields form={{ register, control, errors }} name="password" label="Contraseña" />

                <DataConsentField control={control} />

                <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
                    Crear cuenta <ArrowRight />
                </Button>

                <p className="text-center text-sm text-on-surface-variant">
                    ¿Ya tienes cuenta?{' '}
                    <Link to={ROUTES.LOGIN} className="font-semibold text-primary underline-offset-4 hover:underline">
                        Inicia sesión
                    </Link>
                </p>
            </form>
        </AuthLayout>
    );
}
