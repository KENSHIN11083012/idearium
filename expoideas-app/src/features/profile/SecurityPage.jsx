import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { ShieldCheck } from 'lucide-react';
import { placeServerErrors } from '@/lib/validation';
import { accountApi } from '@/features/auth/api';
import { passwordChangeSchema } from '@/features/auth/schemas';
import { useAuth } from '@/features/auth/useAuth';
import { PageContainer } from '@/components/layout/AppShell';
import { PasswordFields } from '@/components/forms/PasswordFields';
import { PageHeader } from '@/components/ui/page-header';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { PasswordInput } from '@/components/ui/password-input';

const EMPTY = { currentPassword: '', newPassword: '', confirmPassword: '' };

const TIPS = [
    'Usa una contraseña que no uses en otros sitios.',
    'Combina palabras, números y símbolos: la longitud importa más que la complejidad.',
    'Si crees que alguien la conoce, cámbiala de inmediato.',
];

/**
 * Cambio de la propia contraseña. El restablecimiento de contraseñas de otras
 * cuentas está en Gestión > Usuarios.
 */
export default function SecurityPage() {
    const { renewToken } = useAuth();
    const {
        register,
        control,
        handleSubmit,
        reset,
        setError,
        formState: { errors, isSubmitting },
    } = useForm({ resolver: zodResolver(passwordChangeSchema), mode: 'onTouched', defaultValues: EMPTY });

    const submit = async (values) => {
        try {
            const { token } = await accountApi.changePassword(values);
            renewToken(token);
            reset(EMPTY);
            toast.success('Tu contraseña se actualizó', {
                description: 'Cerramos las demás sesiones que tuvieras abiertas.',
            });
        } catch (error) {
            // Lo que no va junto a un campo se avisa: aquí no hay sitio para un error general.
            const general = placeServerErrors(error, setError, control);
            if (general) toast.error(general);
        }
    };

    return (
        <PageContainer>
            <PageHeader
                eyebrow="Mi cuenta"
                title="Seguridad"
                description="Actualiza la contraseña con la que ingresas a Idearium."
            />

            <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
                <Card>
                    <form onSubmit={handleSubmit(submit)} noValidate>
                        <CardHeader>
                            <CardTitle>Cambiar contraseña</CardTitle>
                            <CardDescription>Por seguridad, primero confirma tu contraseña actual.</CardDescription>
                        </CardHeader>
                        <CardContent className="flex flex-col gap-5">
                            <Field label="Contraseña actual" error={errors.currentPassword?.message} required>
                                <PasswordInput autoComplete="current-password" {...register('currentPassword')} />
                            </Field>
                            <PasswordFields form={{ register, control, errors }} />
                        </CardContent>
                        <CardFooter className="justify-end">
                            <Button type="submit" loading={isSubmitting}>
                                Actualizar contraseña
                            </Button>
                        </CardFooter>
                    </form>
                </Card>

                <Card accent="lime" className="h-fit">
                    <CardHeader>
                        <span className="flex size-10 items-center justify-center rounded-lg bg-secondary-container text-on-secondary-fixed">
                            <ShieldCheck className="size-5" aria-hidden="true" />
                        </span>
                        <CardTitle as="h2" className="mt-2">
                            Buenas prácticas
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <ul className="flex flex-col gap-3 text-sm text-on-surface-variant">
                            {TIPS.map((tip) => (
                                <li key={tip} className="flex gap-2">
                                    <span className="mt-2 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                                    {tip}
                                </li>
                            ))}
                        </ul>
                    </CardContent>
                </Card>
            </div>
        </PageContainer>
    );
}
