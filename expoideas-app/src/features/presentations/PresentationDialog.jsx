import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { handleFormError } from '@/lib/validation';
import { FormDialog } from '@/components/forms/FormDialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useSchedulePresentation } from './queries';
import { presentationSchema, toPresentationFormValues, toPresentationRequest } from './schemas';

/**
 * Programar o cambiar la sustentación de un proyecto. Al guardar, la API avisa
 * por correo al equipo y al profesor del grupo.
 *
 * @param {object} project      { id, title } del listado
 * @param {object} presentation la cita actual, o null si es nueva
 */
export function PresentationDialog({ project, presentation, editionId, track, onClose }) {
    const schedule = useSchedulePresentation(editionId, track);
    const {
        register,
        handleSubmit,
        setError,
        control,
        formState: { errors, isSubmitting, isDirty },
    } = useForm({ resolver: zodResolver(presentationSchema), defaultValues: toPresentationFormValues(presentation) });

    const submit = async (values) => {
        try {
            await schedule.mutateAsync({ projectId: project.id, body: toPresentationRequest(values) });
            toast.success(
                presentation ? 'La sustentación se cambió y se avisó al equipo' : 'Sustentación programada; se avisó al equipo',
            );
            onClose();
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <FormDialog
            title={presentation ? 'Cambiar la sustentación' : 'Programar la sustentación'}
            description={`"${project.title}". El equipo y el profesor del grupo reciben un correo con la cita.`}
            onClose={onClose}
            onSubmit={handleSubmit(submit)}
            error={errors.root?.message}
            submitLabel={presentation ? 'Guardar cambios' : 'Programar'}
            submitting={isSubmitting}
            dirty={isDirty}
        >
            <Field label="Fecha y hora" error={errors.startsAt?.message} required>
                <Input type="datetime-local" {...register('startsAt')} />
            </Field>
            <Field label="Lugar" error={errors.place?.message} hint="Auditorio, sala o enlace de la videollamada." required>
                <Input autoComplete="off" {...register('place')} />
            </Field>
            <Field label="Indicaciones" error={errors.notes?.message} hint="Qué llevar, cuánto dura, cómo llegar. Opcional.">
                <Textarea rows={3} {...register('notes')} />
            </Field>
        </FormDialog>
    );
}
