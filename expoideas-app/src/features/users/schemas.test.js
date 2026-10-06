import { describe, expect, it } from 'vitest';
import { ROLES } from '@/lib/roles';
import { newAccountSchemaFor } from './schemas';

const base = {
    firstName: 'Marta',
    lastName: 'Ríos',
    password: 'Temporal#2026',
    campusId: '',
    facultyId: '',
    academicProgramId: '',
};

/** Campos con error, en el orden en que los reporta zod. */
const fieldsWithError = (role, values) => {
    const result = newAccountSchemaFor(role).safeParse({ ...base, role, ...values });
    return result.success ? [] : result.error.issues.map((issue) => issue.path.join('.'));
};

describe('newAccountSchemaFor', () => {
    it('un jurado puede tener correo externo y no lleva adscripción', () => {
        expect(fieldsWithError(ROLES.JUDGE, { email: 'marta@empresa.com' })).toEqual([]);
    });

    it('un jurado igual necesita un correo válido', () => {
        expect(fieldsWithError(ROLES.JUDGE, { email: 'marta' })).toEqual(['email']);
    });

    it('un docente exige correo institucional, sede y facultad', () => {
        expect(fieldsWithError(ROLES.TEACHER, { email: 'pedro@gmail.com' })).toEqual(['email', 'campusId', 'facultyId']);
    });

    it('un estudiante con correo institucional y adscripción es válido; el programa es opcional', () => {
        expect(fieldsWithError(ROLES.STUDENT, { email: 'ana@unisimon.edu.co', campusId: '1', facultyId: '2' })).toEqual([]);
    });

    it('MacondoLab usa correo institucional y no lleva adscripción', () => {
        expect(fieldsWithError(ROLES.MACONDOLAB, { email: 'coordinacion@unisimon.edu.co' })).toEqual([]);
        expect(fieldsWithError(ROLES.MACONDOLAB, { email: 'coordinacion@gmail.com' })).toEqual(['email']);
    });

    it('la contraseña temporal cumple la misma regla que el registro', () => {
        expect(fieldsWithError(ROLES.JUDGE, { email: 'marta@empresa.com', password: 'temporal' })).toEqual(['password']);
    });

    it('recorta espacios del correo antes de validarlo', () => {
        const result = newAccountSchemaFor(ROLES.JUDGE).safeParse({ ...base, role: ROLES.JUDGE, email: '  marta@empresa.com ' });
        expect(result.success).toBe(true);
        expect(result.data.email).toBe('marta@empresa.com');
    });
});
