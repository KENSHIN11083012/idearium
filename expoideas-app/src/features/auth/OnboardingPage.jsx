import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { ArrowRight, MailCheck } from 'lucide-react';
import { affiliationToApi } from '@/lib/affiliation';
import { requiresAffiliation } from '@/lib/roles';
import { ROUTES, homeRouteFor } from '@/lib/routes';
import { handleFormError } from '@/lib/validation';
import { cn } from '@/lib/utils';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { DataConsentField } from '@/components/forms/DataConsentField';
import { PasswordFields } from '@/components/forms/PasswordFields';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { Alert } from '@/components/ui/feedback';
import { AffiliationFields } from '@/features/catalogs/AffiliationFields';
import { useProfile, useUpdateProfile } from '@/features/profile/queries';
import { personalDataSchema, profileSchema } from '@/features/profile/schemas';
import { accountApi } from './api';
import { dataConsentSchema, passwordChangeSchema } from './schemas';
import { useAuth } from './useAuth';

/**
 * La cuenta no sirve hasta abrir el enlace que llegó a su correo: es lo que
 * demuestra que el correo es suyo. Aquí se espera, se pide otro enlace o se
 * comprueba si ya lo abrió (quizá en otro dispositivo).
 */
function VerifyEmailStep({ onDone }) {
    const { user } = useAuth();
    // Solo se consulta al pulsar el botón; el perfil dice si el paso sigue pendiente.
    const profile = useProfile({ enabled: false });
    const [busy, setBusy] = useState(null); // 'check' | 'resend'
    const [notice, setNotice] = useState(null); // { variant, text }

    const check = async () => {
        setBusy('check');
        const { data, error } = await profile.refetch();
        setBusy(null);
        if (error) {
            setNotice({ variant: 'error', text: error.message });
        } else if (data?.pendingSteps?.includes('VERIFY_EMAIL')) {
            setNotice({ variant: 'error', text: 'Todavía no vemos tu correo verificado. Abre el enlace que te enviamos.' });
        } else {
            onDone();
        }
    };

    const resend = async () => {
        setBusy('resend');
        try {
            await accountApi.resendVerification();
            setNotice({ variant: 'success', text: 'Te enviamos un enlace nuevo. El anterior ya no sirve.' });
        } catch (error) {
            setNotice({ variant: 'error', text: error.message });
        } finally {
            setBusy(null);
        }
    };

    return (
        <div className="flex flex-col gap-5">
            {notice && <Alert variant={notice.variant} title={notice.text} />}
            <Alert title={`Te enviamos un enlace a ${user?.email ?? 'tu correo'}`}>
                Ábrelo para confirmar que el correo es tuyo. Vale 48 horas. Si no lo ves, mira en el correo no deseado.
            </Alert>
            <Button
                type="button"
                size="lg"
                loading={busy === 'check'}
                disabled={busy === 'resend'}
                onClick={check}
                className="w-full"
            >
                <MailCheck /> Ya abrí el enlace
            </Button>
            <Button
                type="button"
                variant="outline"
                loading={busy === 'resend'}
                disabled={busy === 'check'}
                onClick={resend}
                className="w-full"
            >
                Enviarme otro enlace
            </Button>
        </div>
    );
}

function ChangePasswordStep({ onDone }) {
    const { renewToken } = useAuth();
    const {
        register,
        control,
        handleSubmit,
        setError,
        formState: { errors, isSubmitting },
    } = useForm({
        resolver: zodResolver(passwordChangeSchema),
        mode: 'onTouched',
        defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
    });

    const submit = async (values) => {
        try {
            // La contraseña temporal ya no vale, ni la sesión que se abrió con ella.
            const { token } = await accountApi.changePassword(values);
            renewToken(token);
            onDone();
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5">
            {errors.root && <Alert variant="error" title={errors.root.message} />}
            <Field
                label="Contraseña temporal"
                hint="La que te entregó MacondoLab o la administración."
                error={errors.currentPassword?.message}
                required
            >
                <PasswordInput autoComplete="current-password" {...register('currentPassword')} />
            </Field>
            <PasswordFields form={{ register, control, errors }} />
            <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
                Guardar y continuar <ArrowRight />
            </Button>
        </form>
    );
}

function DataConsentStep({ onDone }) {
    const {
        control,
        handleSubmit,
        setError,
        formState: { errors, isSubmitting },
    } = useForm({ resolver: zodResolver(dataConsentSchema), defaultValues: { dataConsent: false } });

    const submit = async () => {
        try {
            await accountApi.giveDataConsent();
            onDone();
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5">
            {errors.root && <Alert variant="error" title={errors.root.message} />}
            <DataConsentField control={control} />
            <Button type="submit" size="lg" loading={isSubmitting} className="w-full">
                Aceptar y continuar <ArrowRight />
            </Button>
        </form>
    );
}

/**
 * Nombre y, si el rol la lleva, adscripción académica. Es el mismo formulario
 * de Mi perfil, sin datos previos: el registro solo pidió correo y contraseña.
 */
function CompleteProfileStep({ onDone }) {
    const { role, user } = useAuth();
    const withAffiliation = requiresAffiliation(role);
    const update = useUpdateProfile();
    // Si el listado de la cátedra traía el nombre, viene puesto y se puede corregir.
    const form = useForm({
        resolver: zodResolver(withAffiliation ? profileSchema : personalDataSchema),
        mode: 'onTouched',
        defaultValues: {
            firstName: user?.firstName ?? '',
            lastName: user?.lastName ?? '',
            campusId: '',
            facultyId: '',
            academicProgramId: '',
        },
    });
    const {
        register,
        handleSubmit,
        setError,
        control,
        formState: { errors, isSubmitting },
    } = form;

    const submit = async ({ firstName, lastName, ...affiliation }) => {
        const body = withAffiliation ? { firstName, lastName, ...affiliationToApi(affiliation) } : { firstName, lastName };
        try {
            await update.mutateAsync(body);
            onDone();
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5">
            {errors.root && <Alert variant="error" title={errors.root.message} />}
            <div className="grid gap-5 sm:grid-cols-2">
                <Field label="Nombres" error={errors.firstName?.message} required>
                    <Input autoComplete="given-name" {...register('firstName')} />
                </Field>
                <Field label="Apellidos" error={errors.lastName?.message} required>
                    <Input autoComplete="family-name" {...register('lastName')} />
                </Field>
            </div>
            {withAffiliation && (
                <fieldset className="flex flex-col gap-4 rounded-lg border border-outline-variant/70 p-4">
                    <legend className="px-1 font-heading text-sm font-semibold text-on-surface">
                        Tu vínculo con la universidad
                    </legend>
                    <AffiliationFields form={{ ...form, errors }} />
                </fieldset>
            )}
            <Button type="submit" size="lg" loading={isSubmitting} className="mt-1 w-full">
                Guardar y continuar <ArrowRight />
            </Button>
        </form>
    );
}

/** Pasos que conoce la app, en el orden en que se piden (el mismo de la API). */
const ONBOARDING_STEPS = {
    VERIFY_EMAIL: {
        title: 'Verifica tu correo',
        description: 'Antes de usar tu cuenta necesitamos confirmar que el correo con el que te registraste es tuyo.',
        Step: VerifyEmailStep,
    },
    CHANGE_PASSWORD: {
        title: 'Crea tu contraseña',
        description: 'La contraseña que recibiste es temporal. Elige una propia para proteger tu cuenta.',
        Step: ChangePasswordStep,
    },
    DATA_CONSENT: {
        title: 'Autoriza el tratamiento de tus datos',
        description:
            'Para usar Idearium, la Universidad Simón Bolívar necesita tu autorización para tratar tus datos personales.',
        Step: DataConsentStep,
    },
    COMPLETE_PROFILE: {
        title: 'Completa tu perfil',
        description: 'Cuéntanos cómo te llamas y cuál es tu vínculo con la universidad. Así aparecerás en tus proyectos.',
        Step: CompleteProfileStep,
    },
};

/**
 * Primer ingreso de una cuenta con pasos pendientes (contraseña temporal,
 * autorización de datos, perfil incompleto). La API no deja hacer nada más
 * hasta completarlos. Al terminar el último, la sesión queda sin pendientes y
 * la página lleva al inicio que corresponde al rol.
 */
export default function OnboardingPage() {
    const { token, role, user, pendingSteps, completeStep, logout } = useAuth();
    const navigate = useNavigate();
    const steps = Object.keys(ONBOARDING_STEPS).filter((step) => pendingSteps.includes(step));
    // Cuántos pasos había al entrar, para mostrar "Paso 2 de 2" tras completar el primero.
    const [initialTotal] = useState(steps.length);

    if (!token) return <Navigate to={ROUTES.LOGIN} replace />;
    if (steps.length === 0) return <Navigate to={homeRouteFor(role)} replace />;

    const step = steps[0];
    const { title, description, Step } = ONBOARDING_STEPS[step];
    const total = Math.max(initialTotal, steps.length);
    const current = total - steps.length + 1;

    const complete = () => {
        if (steps.length === 1) toast.success('Todo listo. Ya puedes usar Idearium.');
        completeStep(step);
    };

    const signOut = () => {
        logout();
        navigate(ROUTES.LOGIN, { replace: true });
    };

    return (
        <AuthLayout
            title="Primer ingreso"
            panelTitle="Te damos la bienvenida a Idearium"
            panelText="Antes de empezar, completa tu cuenta. Solo se hace una vez."
        >
            <div className="flex flex-col gap-3">
                <p className="label-mono text-primary">
                    Primer ingreso · Paso {current} de {total}
                </p>
                <div className="flex gap-2" aria-hidden="true">
                    {Array.from({ length: total }, (_, index) => (
                        <span
                            key={index}
                            className={cn('h-1.5 flex-1 rounded-full', index < current ? 'bg-primary' : 'bg-outline-variant')}
                        />
                    ))}
                </div>
                <h1 className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
                <p className="text-on-surface-variant">{description}</p>
            </div>

            <div className="mt-8">
                <Step key={step} onDone={complete} />
            </div>

            <p className="mt-6 text-center text-sm text-on-surface-variant">
                {user?.fullName ? `¿No eres ${user.fullName}? ` : ''}
                <button type="button" onClick={signOut} className="font-semibold text-primary underline-offset-4 hover:underline">
                    Cerrar sesión
                </button>
            </p>
        </AuthLayout>
    );
}
