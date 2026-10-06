import { z } from 'zod';

/** Sedes, facultades, categorías y keywords (CatalogItemRequest). */
export const catalogItemSchema = z.object({
    name: z.string().trim().min(1, 'Ingresa un nombre').max(100, 'Máximo 100 caracteres'),
});

/** Programas académicos (AcademicProgramRequest): el select entrega el id como texto. */
export const academicProgramSchema = z.object({
    name: z.string().trim().min(1, 'Ingresa un nombre').max(150, 'Máximo 150 caracteres'),
    facultyId: z.string().min(1, 'Selecciona la facultad a la que pertenece'),
});
