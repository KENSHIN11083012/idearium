import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { TRACK_DESCRIPTIONS, TRACK_LIST, trackLabel } from '@/lib/tracks';
import { handleFormError } from '@/lib/validation';
import { FormDialog } from '@/components/forms/FormDialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { useSaveEdition } from './queries';
import { editionSchema, toEditionRequest, toFormValues } from './schemas';

/** Límites del grupo de una cátedra. Los define MacondoLab en cada edición. */
function TrackFields({ track, register, errors }) {
    return (
        <fieldset className="flex flex-col gap-3 rounded border border-outline-variant/60 p-4">
            <legend className="label-mono px-1 text-on-surface-variant">{trackLabel(track)}</legend>
            <p className="text-sm text-on-surface-variant">{TRACK_DESCRIPTIONS[track]}</p>
            <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Mínimo de integrantes" error={errors?.minMembers?.message} required>
                    <Input type="number" min="1" max="20" {...register(`tracks.${track}.minMembers`)} />
                </Field>
                <Field label="Máximo de integrantes" error={errors?.maxMembers?.message} required>
                    <Input type="number" min="1" max="20" {...register(`tracks.${track}.maxMembers`)} />
                </Field>
            </div>
        </fieldset>
    );
}

/**
 * Alta y edición de una edición de la Expo.
 *
 * @param {object|null} edition  null para crear
 */
export function EditionDialog({ edition, onClose }) {
    const editing = Boolean(edition);
    const save = useSaveEdition();

    const {
        register,
        handleSubmit,
        setError,
        control,
        formState: { errors, isSubmitting, isDirty },
    } = useForm({
        resolver: zodResolver(editionSchema),
        defaultValues: toFormValues(edition),
    });

    const submit = async (values) => {
        const body = toEditionRequest(values);
        try {
            await save.mutateAsync({ id: edition?.id, body });
            toast.success(editing ? `"${body.name}" se actualizó` : `"${body.name}" se creó`);
            onClose();
        } catch (error) {
            if (error.status === 409) {
                setError('name', { type: 'server', message: 'Ya existe una edición con ese nombre' }, { shouldFocus: true });
            } else {
                handleFormError(error, setError, control);
            }
        }
    };

    return (
        <FormDialog
            title={editing ? 'Editar edición' : 'Nueva edición'}
            description={
                editing
                    ? `Cambia los plazos y la configuración de "${edition.name}".`
                    : 'Define los plazos del semestre y el tamaño de los grupos en cada cátedra.'
            }
            onClose={onClose}
            onSubmit={handleSubmit(submit)}
            error={errors.root?.message}
            submitLabel={editing ? 'Guardar cambios' : 'Crear'}
            submitting={isSubmitting}
            submitDisabled={editing && !isDirty}
            dirty={isDirty}
        >
            <Field label="Nombre" error={errors.name?.message} hint="Por ejemplo, Idearium 2026-2." required>
                <Input autoComplete="off" autoFocus {...register('name')} />
            </Field>

            <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Apertura de inscripciones" error={errors.registrationOpensOn?.message} required>
                    <Input type="date" {...register('registrationOpensOn')} />
                </Field>
                <Field label="Cierre de inscripciones" error={errors.registrationClosesOn?.message} required>
                    <Input type="date" {...register('registrationClosesOn')} />
                </Field>
            </div>

            <Field
                label="Cierre de entregas"
                error={errors.submissionClosesOn?.message}
                hint="Hasta este día los grupos pueden subir sus entregables."
                required
            >
                <Input type="date" {...register('submissionClosesOn')} />
            </Field>

            {TRACK_LIST.map((track) => (
                <TrackFields key={track} track={track} register={register} errors={errors.tracks?.[track]} />
            ))}
        </FormDialog>
    );
}
