import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Gavel, UserPlus, X } from 'lucide-react';
import { toast } from 'sonner';
import { roleLabel } from '@/lib/roles';
import { handleFormError } from '@/lib/validation';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { useJurors, useSaveJuror } from './queries';
import { jurorSchema } from './schemas';

function AssignForm({ projectId }) {
    const save = useSaveJuror(projectId);
    const {
        register,
        handleSubmit,
        reset,
        setError,
        control,
        formState: { errors, isSubmitting },
    } = useForm({ resolver: zodResolver(jurorSchema), defaultValues: { email: '' } });

    const submit = async ({ email }) => {
        try {
            await save.mutateAsync({ email });
            toast.success(`${email} ya es jurado de este proyecto`);
            reset();
        } catch (error) {
            if (error.status === 409) {
                setError('email', { type: 'server', message: error.message }, { shouldFocus: true });
            } else {
                handleFormError(error, setError, control);
            }
        }
    };

    return (
        <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-3 sm:flex-row sm:items-start">
            <Field
                label="Asignar jurado"
                error={errors.email?.message ?? errors.root?.message}
                hint="Un profesor, un jurado externo o alguien de la gestión, con cuenta en Idearium."
                className="flex-1"
            >
                <Input type="email" autoComplete="off" placeholder="correo@dominio.com" {...register('email')} />
            </Field>
            <Button type="submit" variant="outline" loading={isSubmitting} className="sm:mt-6">
                <UserPlus /> Asignar
            </Button>
        </form>
    );
}

/**
 * Los jurados de un proyecto, para la gestión: quiénes son y asignar o quitar.
 * Un jurado asignado ve la ficha y los entregables; la evaluación con rúbrica
 * llega en otra fase.
 */
export function JurorsPanel({ projectId }) {
    const { data: jurors = [], isPending, error, refetch } = useJurors(projectId);
    const save = useSaveJuror(projectId);
    const [confirm, setConfirm] = useState(null);

    const remove = async () => {
        try {
            await save.mutateAsync({ userId: confirm.userId });
            toast.success(`${confirm.fullName} deja de ser jurado`);
        } catch (removeError) {
            toast.error(removeError.message);
        } finally {
            setConfirm(null);
        }
    };

    return (
        <section className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="flex items-center gap-2 font-heading text-lg font-bold text-on-surface">
                    <Gavel className="size-5 text-primary" aria-hidden="true" /> Jurados
                </h2>
                {jurors.length > 0 && (
                    <p className="label-mono text-on-surface-variant">
                        {jurors.length} {jurors.length === 1 ? 'asignado' : 'asignados'}
                    </p>
                )}
            </div>

            {error ? (
                <ErrorState title="No pudimos cargar los jurados" error={error} onRetry={refetch} />
            ) : isPending ? (
                <Skeleton className="h-16 rounded" aria-busy="true" />
            ) : jurors.length === 0 ? (
                <p className="text-sm text-on-surface-variant">Este proyecto todavía no tiene jurados.</p>
            ) : (
                <ul className="flex flex-col gap-2" aria-label="Jurados">
                    {jurors.map((juror) => (
                        <li
                            key={juror.userId}
                            className="flex items-center justify-between gap-3 rounded border border-outline-variant/60 px-4 py-3"
                        >
                            <div className="flex flex-col gap-0.5">
                                <p className="flex items-center gap-2 font-medium text-on-surface">
                                    {juror.fullName}
                                    <Badge variant="outline" mono>
                                        {roleLabel(juror.role)}
                                    </Badge>
                                </p>
                                <p className="text-xs text-on-surface-variant">{juror.email}</p>
                            </div>
                            <Button
                                variant="ghost"
                                size="icon-sm"
                                onClick={() => setConfirm(juror)}
                                aria-label={`Quitar a ${juror.fullName} como jurado`}
                            >
                                <X />
                            </Button>
                        </li>
                    ))}
                </ul>
            )}

            <AssignForm projectId={projectId} />

            <ConfirmDialog
                open={Boolean(confirm)}
                title="¿Quitar al jurado?"
                description={`${confirm?.fullName ?? ''} dejará de ver este proyecto y sus entregables.`}
                confirmLabel="Quitar"
                onConfirm={remove}
                onClose={() => setConfirm(null)}
            />
        </section>
    );
}
