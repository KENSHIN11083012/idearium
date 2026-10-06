import { z } from 'zod';
import { affiliationShape } from '@/lib/affiliation';
import { ROLES, requiresAffiliation } from '@/lib/roles';
import { INSTITUTIONAL_DOMAIN, newPasswordShape, passwordsMatch, rules } from '@/lib/validation';

/**
 * Alta de una cuenta desde la gestión. Las reglas dependen del rol elegido, como
 * en la API: correo externo solo para jurados y adscripción solo para docentes y
 * estudiantes.
 */
export const newAccountSchemaFor = (role) =>
    z.object({
        role: z.string().min(1, 'Selecciona el rol'),
        firstName: rules.firstName('Ingresa los nombres'),
        lastName: rules.lastName('Ingresa los apellidos'),
        email:
            role === ROLES.JUDGE
                ? rules.anyEmail()
                : rules.institutionalEmail(
                      `Usa un correo ${INSTITUTIONAL_DOMAIN}; solo los jurados pueden tener uno externo`,
                      'Ingresa el correo',
                  ),
        password: rules.password(),
        ...(requiresAffiliation(role) ? affiliationShape : {}),
    });

/** Restablecimiento por la gestión: sin la contraseña actual. */
export const passwordResetSchema = z.object(newPasswordShape).refine(...passwordsMatch);

export const affiliationSchema = z.object(affiliationShape);
