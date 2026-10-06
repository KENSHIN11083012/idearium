import { z } from 'zod';
import { rules } from '@/lib/validation';

/** Inscripción de un proyecto (ProjectRequest). Los selects entregan ids como texto. */
export const projectSchema = z.object({
    editionId: z.string().min(1, 'Selecciona la edición'),
    track: z.string().min(1, 'Selecciona la cátedra'),
    title: z.string().trim().min(1, 'Ingresa el título del proyecto').max(150, 'Máximo 150 caracteres'),
    summary: z.string().trim().min(1, 'Describe tu propuesta de valor').max(500, 'Máximo 500 caracteres'),
    sectorId: z.string().min(1, 'Selecciona el sector'),
    /** Solo en INNPRENDE II. Si el catálogo tiene tipos, la API lo exige y devuelve el error en el campo. */
    prototypeTypeId: z.string(),
    teacherId: z.string().min(1, 'Selecciona el profesor del grupo'),
});

/** Invitación a un compañero: la API exige que la cuenta exista. */
export const invitationSchema = z.object({
    email: rules.institutionalEmail('Usa el correo institucional de tu compañero', 'Ingresa el correo'),
});

/** Del formulario al cuerpo que espera la API. */
export const toProjectRequest = (values) => ({
    editionId: Number(values.editionId),
    track: values.track,
    title: values.title,
    summary: values.summary,
    sectorId: Number(values.sectorId),
    prototypeTypeId: values.prototypeTypeId ? Number(values.prototypeTypeId) : null,
    teacherId: Number(values.teacherId),
});

/** Del proyecto que devuelve la API a los valores del formulario. */
export const toFormValues = (project) => ({
    editionId: project ? String(project.editionId) : '',
    track: project?.track ?? '',
    title: project?.title ?? '',
    summary: project?.summary ?? '',
    sectorId: project ? String(project.sectorId) : '',
    prototypeTypeId: project?.prototypeTypeId ? String(project.prototypeTypeId) : '',
    teacherId: project ? String(project.teacherId) : '',
});

/** Resultado del proyecto al cerrar la cátedra. Espejo de ProjectResult en la API. */
export const RESULTS = {
    APPROVED: 'APPROVED',
    NOT_APPROVED: 'NOT_APPROVED',
};

export const RESULT_LABELS = {
    [RESULTS.APPROVED]: 'Aprobado',
    [RESULTS.NOT_APPROVED]: 'No aprobado',
};

export const resultLabel = (result) => RESULT_LABELS[result] ?? 'Sin resultado';
