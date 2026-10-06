import { useMemo } from 'react';
import { useWatch } from 'react-hook-form';
import { Field } from '@/components/ui/field';
import { NativeSelect } from '@/components/ui/native-select';
import { Alert, Skeleton } from '@/components/ui/feedback';
import { useAffiliationCatalogs } from './queries';

/**
 * Sede, facultad y programa académico para formularios de react-hook-form con
 * los campos de affiliationShape (lib/affiliation.js). El programa se filtra por
 * la facultad elegida y es opcional: los docentes pueden pertenecer a una
 * facultad sin estar en un programa. Los catálogos se piden al montarse y quedan
 * en caché para los demás formularios.
 *
 * @param {object} form  { register, control, setValue, errors } del useForm
 */
export function AffiliationFields({ form }) {
    const { register, control, setValue, errors } = form;
    const { campuses, faculties, programs, isPending, error } = useAffiliationCatalogs();
    const facultyId = useWatch({ control, name: 'facultyId' });

    const facultyPrograms = useMemo(
        () => programs.filter((program) => String(program.facultyId) === facultyId),
        [programs, facultyId],
    );

    // Al cambiar de facultad, un programa de la anterior deja de ser válido.
    const facultyField = register('facultyId');
    const onFacultyChange = (event) => {
        facultyField.onChange(event);
        setValue('academicProgramId', '', { shouldDirty: true });
    };

    if (error) {
        return (
            <Alert variant="error" title="No pudimos cargar las sedes y facultades">
                {error.message}
            </Alert>
        );
    }

    // Los <select> se montan cuando ya tienen opciones: si se montaran vacíos, el
    // valor inicial del formulario no encontraría su opción y se vería en blanco.
    if (isPending) {
        return (
            <div className="grid gap-5 sm:grid-cols-2" aria-busy="true">
                {[0, 1, 2].map((index) => (
                    <div key={index} className={index === 2 ? 'flex flex-col gap-1.5 sm:col-span-2' : 'flex flex-col gap-1.5'}>
                        <Skeleton className="h-3 w-24" />
                        <Skeleton className="h-11" />
                    </div>
                ))}
            </div>
        );
    }

    const programHint = !facultyId
        ? 'Primero elige la facultad.'
        : facultyPrograms.length === 0
          ? 'Esta facultad aún no tiene programas registrados.'
          : 'Opcional para profesores.';

    return (
        <div className="grid gap-5 sm:grid-cols-2">
            <Field label="Sede" error={errors.campusId?.message} required>
                <NativeSelect {...register('campusId')}>
                    <option value="">Selecciona la sede</option>
                    {campuses.map((campus) => (
                        <option key={campus.id} value={String(campus.id)}>
                            {campus.name}
                        </option>
                    ))}
                </NativeSelect>
            </Field>

            <Field label="Facultad" error={errors.facultyId?.message} required>
                <NativeSelect {...facultyField} onChange={onFacultyChange}>
                    <option value="">Selecciona la facultad</option>
                    {faculties.map((faculty) => (
                        <option key={faculty.id} value={String(faculty.id)}>
                            {faculty.name}
                        </option>
                    ))}
                </NativeSelect>
            </Field>

            <Field
                label="Programa académico"
                error={errors.academicProgramId?.message}
                hint={programHint}
                className="sm:col-span-2"
            >
                <NativeSelect disabled={!facultyId} {...register('academicProgramId')}>
                    <option value="">{facultyId ? 'Sin programa' : '—'}</option>
                    {facultyPrograms.map((program) => (
                        <option key={program.id} value={String(program.id)}>
                            {program.name}
                        </option>
                    ))}
                </NativeSelect>
            </Field>
        </div>
    );
}
