import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight } from 'lucide-react';
import { ROUTES } from '@/lib/routes';
import { handleFormError } from '@/lib/validation';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { AuthHeading } from '@/components/layout/AuthHeading';
import { PasswordFields } from '@/components/forms/PasswordFields';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/feedback';
import { authApi } from './api';
import { linkToken } from './linkToken';
import { passwordResetSchema } from './schemas';

const requestAnother = (
    <Link to={ROUTES.RECOVER_PASSWORD} className="font-semibold text-primary underline-offset-4 hover:underline">
        Pedir un enlace nuevo
    </Link>
);

/**
 * Adonde lleva el enlace de recuperación: aquí se pone la contraseña nueva. El
 * token viene en el fragmento de la dirección y se envía junto con ella.
 */
export default function ResetPasswordPage() {
    const { hash } = useLocation();
    const navigate = useNavigate();
    const token = linkToken(hash);
    const {
        register,
        control,
        handleSubmit,
        setError,
        formState: { errors, isSubmitting },
    } = useForm({
        resolver: zodResolver(passwordResetSchema),
        mode: 'onTouched',
        defaultValues: { newPassword: '', confirmPassword: '' },
    });

    const submit = async (values) => {
        try {
            await authApi.resetPassword({ token, ...values });
            navigate(ROUTES.LOGIN, { state: { message: 'Tu contraseña se cambió. Ya puedes iniciar sesión.' } });
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <AuthLayout
            title="Nueva contraseña"
            panelTitle="Elige una contraseña que solo tú conozcas"
            panelText="Al cambiarla se cierran las sesiones que estuvieran abiertas con la anterior."
        >
            <AuthHeading eyebrow="Acceso" title="Pon tu contraseña nueva" />

            <div className="mt-8 flex flex-col gap-5">
                {!token ? (
                    <>
                        <Alert variant="error" title="El enlace está incompleto">
                            Ábrelo de nuevo desde el correo que te enviamos, o pide otro.
                        </Alert>
                        <p className="text-center text-sm">{requestAnother}</p>
                    </>
                ) : (
                    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5">
                        {errors.root && (
                            <Alert variant="error" title={errors.root.message}>
                                {requestAnother}
                            </Alert>
                        )}
                        <PasswordFields form={{ register, control, errors }} />
                        <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
                            Guardar la contraseña <ArrowRight />
                        </Button>
                    </form>
                )}
            </div>
        </AuthLayout>
    );
}
