import { useForm, useWatch } from 'react-hook-form';
import { Check, X } from 'lucide-react';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { affiliationFromUser, affiliationToApi } from '@/lib/affiliation';
import { ROLES, ROLE_LABELS, assignableRoles, requiresAffiliation } from '@/lib/roles';
import { TRACK_LIST, trackCourse } from '@/lib/tracks';
import { INSTITUTIONAL_DOMAIN, handleFormError } from '@/lib/validation';
import { FormDialog } from '@/components/forms/FormDialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { PasswordFields } from '@/components/forms/PasswordFields';
import { PasswordRequirements } from '@/components/forms/PasswordRequirements';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { NativeSelect } from '@/components/ui/native-select';
import { PasswordInput } from '@/components/ui/password-input';
import { AffiliationFields } from '@/features/catalogs/AffiliationFields';
import { useCreateUser, useResetPassword, useSaveTrackApproval, useTrackApprovals, useUpdateUser } from './queries';
import { affiliationSchema, newAccountSchemaFor, passwordResetSchema } from './schemas';

export function PasswordResetDialog({ user, onClose }) {
    const reset = useResetPassword();
    const {
        register,
        control,
        handleSubmit,
        setError,
        formState: { errors, isSubmitting, isDirty },
    } = useForm({
        resolver: zodResolver(passwordResetSchema),
        mode: 'onTouched',
        defaultValues: { newPassword: '', confirmPassword: '' },
    });

    const submit = async (passwords) => {
        try {
            await reset.mutateAsync({ id: user.id, passwords });
            toast.success(`Contraseña restablecida para ${user.email}`);
            onClose();
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <FormDialog
            title="Restablecer contraseña"
            description={`Nueva contraseña para ${user.firstName} ${user.lastName} (${user.email}). Compártela por un canal seguro: se le pedirá cambiarla al ingresar.`}
            onClose={onClose}
            onSubmit={handleSubmit(submit)}
            error={errors.root?.message}
            submitLabel="Restablecer"
            submitting={isSubmitting}
            dirty={isDirty}
        >
            <PasswordFields form={{ register, control, errors }} />
        </FormDialog>
    );
}

export function AffiliationDialog({ user, onClose }) {
    const update = useUpdateUser();
    const form = useForm({ resolver: zodResolver(affiliationSchema), defaultValues: affiliationFromUser(user) });
    const {
        handleSubmit,
        setError,
        control,
        formState: { errors, isSubmitting, isDirty },
    } = form;

    const submit = async (values) => {
        try {
            await update.mutateAsync({ id: user.id, changes: affiliationToApi(values) });
            toast.success(`Adscripción de ${user.firstName} actualizada`);
            onClose();
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <FormDialog
            title="Editar adscripción"
            description={`Campus, facultad y programa de ${user.firstName} ${user.lastName}.`}
            onClose={onClose}
            onSubmit={handleSubmit(submit)}
            error={errors.root?.message}
            submitLabel="Guardar"
            submitting={isSubmitting}
            dirty={isDirty}
            submitDisabled={!isDirty}
        >
            <AffiliationFields form={{ ...form, errors }} />
        </FormDialog>
    );
}

export function NewUserDialog({ actor, onClose }) {
    const create = useCreateUser();
    const allowedRoles = assignableRoles(actor);
    const form = useForm({
        // El esquema se arma con el rol elegido en cada validación.
        resolver: (values, context, options) => zodResolver(newAccountSchemaFor(values.role))(values, context, options),
        mode: 'onTouched',
        defaultValues: {
            // El caso más común es un jurado externo.
            role: allowedRoles.includes(ROLES.JUDGE) ? ROLES.JUDGE : allowedRoles[0],
            firstName: '',
            lastName: '',
            email: '',
            password: '',
            campusId: '',
            facultyId: '',
            academicProgramId: '',
        },
    });
    const {
        register,
        control,
        handleSubmit,
        setError,
        formState: { errors, isSubmitting, isDirty },
    } = form;
    const [role, password] = useWatch({ control, name: ['role', 'password'] });

    const submit = async ({ campusId, facultyId, academicProgramId, ...values }) => {
        const body = {
            ...values,
            ...(requiresAffiliation(values.role) ? affiliationToApi({ campusId, facultyId, academicProgramId }) : {}),
        };
        try {
            const created = await create.mutateAsync(body);
            toast.success(`Cuenta de ${created.firstName} ${created.lastName} creada`);
            onClose();
        } catch (error) {
            if (error.status === 409) {
                setError('email', { type: 'server', message: error.message }, { shouldFocus: true });
            } else {
                handleFormError(error, setError, control);
            }
        }
    };

    return (
        <FormDialog
            title="Nueva cuenta"
            description="Para jurados externos o personas que necesitan otro rol. Los estudiantes pueden crear su cuenta por sí mismos."
            onClose={onClose}
            onSubmit={handleSubmit(submit)}
            error={errors.root?.message}
            submitLabel="Crear cuenta"
            submitting={isSubmitting}
            dirty={isDirty}
        >
            <Field label="Rol" error={errors.role?.message} required>
                <NativeSelect {...register('role')}>
                    {allowedRoles.map((option) => (
                        <option key={option} value={option}>
                            {ROLE_LABELS[option]}
                        </option>
                    ))}
                </NativeSelect>
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Nombres" error={errors.firstName?.message} required>
                    <Input autoComplete="off" {...register('firstName')} />
                </Field>
                <Field label="Apellidos" error={errors.lastName?.message} required>
                    <Input autoComplete="off" {...register('lastName')} />
                </Field>
            </div>
            <Field
                label="Correo"
                error={errors.email?.message}
                hint={
                    role === ROLES.JUDGE ? 'Puede ser personal o de su organización.' : `Debe terminar en ${INSTITUTIONAL_DOMAIN}`
                }
                required
            >
                <Input type="email" inputMode="email" autoComplete="off" {...register('email')} />
            </Field>
            <div className="flex flex-col gap-2">
                <Field
                    label="Contraseña temporal"
                    error={errors.password?.message}
                    hint="Entrégala por un canal seguro; se le pedirá cambiarla en su primer ingreso."
                    required
                >
                    <PasswordInput autoComplete="new-password" {...register('password')} />
                </Field>
                <PasswordRequirements value={password} className="sm:grid-cols-1" />
            </div>
            {requiresAffiliation(role) && (
                <fieldset className="flex flex-col gap-4 rounded-lg border border-outline-variant/70 p-4">
                    <legend className="px-1 font-heading text-sm font-semibold text-on-surface">Adscripción académica</legend>
                    <AffiliationFields form={{ ...form, errors }} />
                </fieldset>
            )}
        </FormDialog>
    );
}

/**
 * Qué cátedras tiene aprobadas una persona. Las que nacen de un proyecto las
 * escribe el resultado del proyecto; la gestión registra a mano las de quien
 * cursó INNPRENDE I antes de existir la plataforma, y puede quitar cualquiera.
 */
export function TrackApprovalsDialog({ user, onClose }) {
    const { data: approvals = [], isPending, error, refetch } = useTrackApprovals(user.id);
    const save = useSaveTrackApproval(user.id);
    const name = `${user.firstName} ${user.lastName}`;

    const approve = async (track) => {
        try {
            await save.mutateAsync({ track });
            toast.success(`${trackCourse(track)} aprobada para ${name}`);
        } catch (saveError) {
            toast.error(saveError.message);
        }
    };

    const revoke = async (approval) => {
        try {
            await save.mutateAsync({ id: approval.id });
            toast.success(`Se quitó la aprobación de ${trackCourse(approval.track)}`);
        } catch (saveError) {
            toast.error(saveError.message);
        }
    };

    return (
        <Dialog open onOpenChange={(open) => !open && onClose()}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Cátedras aprobadas</DialogTitle>
                    <DialogDescription>
                        {name}. Para inscribirse en INNPRENDE II hay que tener aprobada INNPRENDE I.
                    </DialogDescription>
                </DialogHeader>
                <DialogBody>
                    {error ? (
                        <ErrorState title="No pudimos cargar las aprobaciones" error={error} onRetry={refetch} />
                    ) : isPending ? (
                        <Skeleton className="h-24" aria-busy="true" />
                    ) : (
                        <ul className="flex flex-col gap-2">
                            {TRACK_LIST.map((track) => {
                                const approval = approvals.find((item) => item.track === track);
                                return (
                                    <li
                                        key={track}
                                        className="flex flex-wrap items-center justify-between gap-3 rounded border border-outline-variant/60 px-4 py-3"
                                    >
                                        <div className="flex flex-col gap-1">
                                            <p className="font-medium text-on-surface">{trackCourse(track)}</p>
                                            {approval ? (
                                                <p className="text-xs text-on-surface-variant">
                                                    {approval.projectTitle
                                                        ? `Por el proyecto "${approval.projectTitle}"`
                                                        : 'Registrada a mano'}
                                                    {approval.approvedBy ? ` · ${approval.approvedBy}` : ''}
                                                </p>
                                            ) : (
                                                <p className="text-xs text-on-surface-variant">Sin aprobar</p>
                                            )}
                                        </div>
                                        {approval ? (
                                            <div className="flex items-center gap-2">
                                                <Badge variant="primary">Aprobada</Badge>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => revoke(approval)}
                                                    loading={save.isPending}
                                                    aria-label={`Quitar la aprobación de ${trackCourse(track)}`}
                                                >
                                                    <X /> Quitar
                                                </Button>
                                            </div>
                                        ) : (
                                            <Button
                                                variant="outline"
                                                size="sm"
                                                onClick={() => approve(track)}
                                                loading={save.isPending}
                                            >
                                                <Check /> Marcar como aprobada
                                            </Button>
                                        )}
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </DialogBody>
                <DialogFooter>
                    <Button type="button" onClick={onClose}>
                        Listo
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
