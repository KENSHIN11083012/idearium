import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight } from 'lucide-react';
import { ROUTES } from '@/lib/routes';
import { handleFormError } from '@/lib/validation';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { AuthHeading } from '@/components/layout/AuthHeading';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/feedback';
import { authApi } from './api';
import { passwordRecoverySchema } from './schemas';

/**
 * Pide el enlace para poner una contraseña nueva. La respuesta es la misma haya
 * o no una cuenta con ese correo: la página no sirve para averiguar quién está
 * registrado.
 */
export default function RecoverPasswordPage() {
    const [sentTo, setSentTo] = useState(null);
    const {
        register,
        handleSubmit,
        setError,
        control,
        formState: { errors, isSubmitting },
    } = useForm({ resolver: zodResolver(passwordRecoverySchema), defaultValues: { email: '' } });

    const submit = async ({ email }) => {
        try {
            await authApi.requestPasswordRecovery(email);
            setSentTo(email);
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <AuthLayout
            title="Recuperar contraseña"
            panelTitle="Todos olvidamos una contraseña"
            panelText="Te enviamos un enlace a tu correo para que pongas una nueva."
        >
            <AuthHeading eyebrow="Acceso" title="Recupera tu contraseña">
                Escribe el correo con el que entras a Idearium y te enviamos un enlace para poner una contraseña nueva.
            </AuthHeading>

            <div className="mt-8 flex flex-col gap-5">
                {sentTo ? (
                    <Alert variant="success" title="Revisa tu correo">
                        Si hay una cuenta con {sentTo}, le enviamos un enlace que vale una hora. Si no llega en unos minutos, mira
                        en el correo no deseado.
                    </Alert>
                ) : (
                    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5">
                        {errors.root && <Alert variant="error" title={errors.root.message} />}
                        <Field label="Correo" error={errors.email?.message} required>
                            <Input type="email" autoComplete="username" inputMode="email" {...register('email')} />
                        </Field>
                        <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
                            Enviar el enlace <ArrowRight />
                        </Button>
                    </form>
                )}

                <p className="text-center text-sm text-on-surface-variant">
                    <Link to={ROUTES.LOGIN} className="font-semibold text-primary underline-offset-4 hover:underline">
                        Volver a iniciar sesión
                    </Link>
                </p>
            </div>
        </AuthLayout>
    );
}
