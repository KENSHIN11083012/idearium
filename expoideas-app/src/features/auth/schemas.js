import { z } from 'zod';
import { newPasswordShape, passwordsMatch, rules } from '@/lib/validation';

export const loginSchema = z.object({
    email: z.string().trim().min(1, 'Ingresa tu correo institucional'),
    password: z.string().min(1, 'Ingresa tu contraseña'),
});

/** Registro mínimo: el nombre y la adscripción se piden en el primer ingreso. */
export const registrationSchema = z
    .object({
        email: rules.institutionalEmail(),
        password: rules.password(),
        confirmPassword: z.string().min(1, 'Confirma tu contraseña'),
        dataConsent: rules.dataConsent('Debes autorizar el tratamiento de tus datos para crear la cuenta'),
    })
    .refine((values) => values.password === values.confirmPassword, {
        message: 'Las contraseñas no coinciden',
        path: ['confirmPassword'],
    });

/** Cambio de la propia contraseña: exige la actual. */
export const passwordChangeSchema = z
    .object({
        currentPassword: z.string().min(1, 'Ingresa tu contraseña actual'),
        ...newPasswordShape,
    })
    .refine(...passwordsMatch)
    .refine((values) => values.newPassword !== values.currentPassword, {
        message: 'La nueva contraseña debe ser distinta de la actual',
        path: ['newPassword'],
    });

/** El correo de la cuenta cuya contraseña se quiere recuperar; puede ser el de un jurado externo. */
export const passwordRecoverySchema = z.object({ email: rules.anyEmail() });

/** Contraseña nueva desde el enlace de recuperación: no se conoce la anterior. */
export const passwordResetSchema = z.object(newPasswordShape).refine(...passwordsMatch);

/** Autorización de datos en el primer ingreso de una cuenta creada desde la gestión. */
export const dataConsentSchema = z.object({
    dataConsent: rules.dataConsent('Debes autorizar el tratamiento de tus datos para usar la plataforma'),
});
