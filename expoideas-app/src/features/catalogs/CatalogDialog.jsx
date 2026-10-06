import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { byName } from '@/lib/text';
import { handleFormError } from '@/lib/validation';
import { FormDialog } from '@/components/forms/FormDialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { NativeSelect } from '@/components/ui/native-select';
import { Skeleton } from '@/components/ui/feedback';
import { CATALOG_PATHS } from './api';
import { useCatalogItems, useSaveCatalogItem } from './queries';
import { academicProgramSchema, catalogItemSchema } from './schemas';

/**
 * Facultades para el select de los programas; solo se piden si el catálogo las
 * usa. El select se monta con las opciones ya cargadas, para que al editar se
 * vea la facultad actual.
 */
function FacultySelect({ register, error }) {
    const { data: faculties = [], isPending } = useCatalogItems(CATALOG_PATHS.faculties);
    if (isPending) {
        return <Skeleton className="h-16" aria-busy="true" />;
    }
    return (
        <Field
            label="Facultad"
            error={error}
            hint={faculties.length === 0 ? 'Primero crea al menos una facultad.' : undefined}
            required
        >
            <NativeSelect {...register('facultyId')}>
                <option value="">Selecciona una facultad</option>
                {[...faculties].sort(byName).map((faculty) => (
                    <option key={faculty.id} value={String(faculty.id)}>
                        {faculty.name}
                    </option>
                ))}
            </NativeSelect>
        </Field>
    );
}

/**
 * Alta y edición de un registro de catálogo. Los programas académicos piden
 * además la facultad.
 *
 * @param {object} catalog    { path, singular, newLabel, article, hasFaculty }
 * @param {object|null} item  null para crear
 */
export function CatalogDialog({ catalog, item, onClose }) {
    const editing = Boolean(item);
    const { hasFaculty } = catalog;
    const save = useSaveCatalogItem(catalog.path);

    const {
        register,
        handleSubmit,
        setError,
        control,
        formState: { errors, isSubmitting, isDirty },
    } = useForm({
        resolver: zodResolver(hasFaculty ? academicProgramSchema : catalogItemSchema),
        defaultValues: hasFaculty
            ? { name: item?.name ?? '', facultyId: item?.facultyId != null ? String(item.facultyId) : '' }
            : { name: item?.name ?? '' },
    });

    const submit = async (values) => {
        const body = hasFaculty ? { name: values.name, facultyId: Number(values.facultyId) } : { name: values.name };
        try {
            await save.mutateAsync({ id: item?.id, body });
            toast.success(editing ? `"${body.name}" se actualizó` : `"${body.name}" se agregó`);
            onClose();
        } catch (error) {
            if (error.status === 409) {
                setError(
                    'name',
                    { type: 'server', message: `Ya existe ${catalog.article} con ese nombre` },
                    { shouldFocus: true },
                );
            } else {
                handleFormError(error, setError, control);
            }
        }
    };

    return (
        <FormDialog
            title={editing ? `Editar ${catalog.singular}` : catalog.newLabel}
            description={editing ? `Cambia los datos de "${item.name}".` : `Agrega ${catalog.article} al catálogo.`}
            onClose={onClose}
            onSubmit={handleSubmit(submit)}
            error={errors.root?.message}
            submitLabel={editing ? 'Guardar cambios' : 'Crear'}
            submitting={isSubmitting}
            submitDisabled={editing && !isDirty}
            dirty={isDirty}
        >
            <Field label="Nombre" error={errors.name?.message} required>
                <Input autoComplete="off" autoFocus {...register('name')} />
            </Field>
            {hasFaculty && <FacultySelect register={register} error={errors.facultyId?.message} />}
        </FormDialog>
    );
}
