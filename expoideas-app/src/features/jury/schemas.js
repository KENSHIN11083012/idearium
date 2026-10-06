import { z } from 'zod';
import { ROLES } from '@/lib/roles';
import { rules } from '@/lib/validation';

/** Asignar un jurado por el correo de su cuenta (JurorRequest). Puede ser externo. */
export const jurorSchema = z.object({ email: rules.anyEmail() });

/** Roles que pueden ser jurados. Espejo de JuryService.canBeJuror. */
export const canBeJuror = (role) => [ROLES.TEACHER, ROLES.JUDGE, ROLES.MACONDOLAB, ROLES.ADMIN].includes(role);
