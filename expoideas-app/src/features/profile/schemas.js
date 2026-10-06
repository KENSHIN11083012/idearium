import { z } from 'zod';
import { affiliationShape } from '@/lib/affiliation';
import { rules } from '@/lib/validation';

/** Lo que cualquier rol edita de sí mismo. */
export const personalDataSchema = z.object({
    firstName: rules.firstName(),
    lastName: rules.lastName(),
});

/** Perfil de los roles con adscripción académica (docentes y estudiantes). */
export const profileSchema = personalDataSchema.extend(affiliationShape);
