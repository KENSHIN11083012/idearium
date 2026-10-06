import { describe, expect, it } from 'vitest';
import { ROLES, asRole, assignableRoles, canManage, hasRole, isManagement, requiresAffiliation, roleLabel } from './roles';
import { ROUTES, homeRouteFor, startRouteFor } from './routes';

const { ADMIN, MACONDOLAB, TEACHER, JUDGE, STUDENT } = ROLES;
const ALL = [STUDENT, TEACHER, JUDGE, MACONDOLAB, ADMIN];

describe('roles (espejo de Role en la API)', () => {
    it('reconoce solo los roles de la API', () => {
        expect(asRole('JUDGE')).toBe(JUDGE);
        expect(asRole('judge')).toBeNull();
        expect(asRole(undefined)).toBeNull();
        expect(roleLabel('MENTOR')).toBe('Usuario');
    });

    it('solo MacondoLab y el administrador son de gestión', () => {
        expect(ALL.filter(isManagement)).toEqual([MACONDOLAB, ADMIN]);
    });

    it('solo docentes y estudiantes declaran adscripción', () => {
        expect(ALL.filter(requiresAffiliation)).toEqual([STUDENT, TEACHER]);
    });

    it('el administrador gestiona cualquier cuenta', () => {
        expect(ALL.every((role) => canManage(ADMIN, role))).toBe(true);
        expect(assignableRoles(ADMIN)).toEqual(ALL);
    });

    it('MacondoLab no gestiona cuentas de gestión', () => {
        expect(assignableRoles(MACONDOLAB)).toEqual([STUDENT, TEACHER, JUDGE]);
        expect(canManage(MACONDOLAB, ADMIN)).toBe(false);
    });

    it('docentes, jurados y estudiantes no gestionan cuentas', () => {
        for (const role of [TEACHER, JUDGE, STUDENT]) {
            expect(assignableRoles(role)).toEqual([]);
        }
        expect(assignableRoles(null)).toEqual([]);
    });

    it('hasRole sin restricción deja pasar a cualquiera con sesión', () => {
        expect(hasRole(JUDGE)).toBe(true);
        expect(hasRole(JUDGE, [MACONDOLAB, ADMIN])).toBe(false);
        expect(hasRole(MACONDOLAB, [MACONDOLAB, ADMIN])).toBe(true);
    });
});

describe('rutas de inicio', () => {
    it('la gestión aterriza en Usuarios y el resto en el inicio', () => {
        expect(homeRouteFor(MACONDOLAB)).toBe(ROUTES.USERS);
        expect(homeRouteFor(ADMIN)).toBe(ROUTES.USERS);
        expect(homeRouteFor(JUDGE)).toBe(ROUTES.HOME);
    });

    it('con pasos pendientes, primero el primer ingreso', () => {
        expect(startRouteFor(ADMIN, ['DATA_CONSENT'])).toBe(ROUTES.ONBOARDING);
        expect(startRouteFor(ADMIN, [])).toBe(ROUTES.USERS);
    });
});
