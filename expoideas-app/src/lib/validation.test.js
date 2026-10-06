import { describe, expect, it, vi } from 'vitest';
import { apiError } from '@/test/utils';
import { PASSWORD_REQUIREMENTS, handleFormError, placeServerErrors, rules } from './validation';

const accepts = (password) => rules.password().safeParse(password).success;

describe('la contraseña', () => {
    it('pide entre 8 y 72 caracteres, con un número y un símbolo', () => {
        expect(accepts('Clave-2026')).toBe(true);
        expect(accepts('Corta-1')).toBe(false);
        expect(accepts('SinNumero-')).toBe(false);
        expect(accepts('SinSimbolo2026')).toBe(false);
        expect(accepts(`${'a'.repeat(70)}-1`)).toBe(true);
        expect(accepts(`${'a'.repeat(71)}-1`)).toBe(false);
    });

    // La API la guarda con BCrypt, que solo lee 72 bytes: una tilde o una ñ ocupan dos.
    it('cuenta bytes y no letras: 40 eñes ya pasan del límite', () => {
        const password = `${'ñ'.repeat(40)}-1`;

        expect(password).toHaveLength(42);
        expect(accepts(password)).toBe(false);
        expect(accepts(`${'ñ'.repeat(35)}-1`)).toBe(true);
    });

    it('la lista de requisitos marca el largo con la misma cuenta', () => {
        const length = PASSWORD_REQUIREMENTS[0];

        expect(length.text).toContain('72');
        expect(length.met('Clave-2026')).toBe(true);
        expect(length.met(`${'ñ'.repeat(40)}-1`)).toBe(false);
    });
});

describe('los errores de la API en un formulario', () => {
    /** Un formulario con esos campos en pantalla. */
    const form = (...fields) => ({
        setError: vi.fn(),
        control: { _names: { mount: new Set(fields.length > 0 ? fields : ['name', 'maxFiles']) } },
    });

    it('el de un campo va junto a ese campo, y el primero se enfoca', () => {
        const { setError, control } = form();
        const error = apiError('Los datos enviados no son válidos.', 400, {
            fields: { name: 'El nombre es obligatorio', maxFiles: 'Máximo 10' },
        });

        handleFormError(error, setError, control);

        expect(setError).toHaveBeenCalledWith(
            'name',
            { type: 'server', message: 'El nombre es obligatorio' },
            { shouldFocus: true },
        );
        expect(setError).toHaveBeenCalledWith('maxFiles', { type: 'server', message: 'Máximo 10' }, { shouldFocus: false });
        expect(setError).not.toHaveBeenCalledWith('root', expect.anything());
    });

    it('el de un campo que el formulario no tiene no se pierde: sale como error general', () => {
        const { setError, control } = form();
        const error = apiError('Los datos enviados no son válidos.', 400, {
            fields: { name: 'El nombre es obligatorio', sortOrder: 'El orden es obligatorio' },
        });

        handleFormError(error, setError, control);

        expect(setError).toHaveBeenCalledWith('name', expect.anything(), { shouldFocus: true });
        expect(setError).not.toHaveBeenCalledWith('sortOrder', expect.anything(), expect.anything());
        expect(setError).toHaveBeenCalledWith('root', { type: 'server', message: 'El orden es obligatorio' });
    });

    it('un campo anidado va a su sitio si está en pantalla', () => {
        const { setError, control } = form('name', 'tracks.INNPRENDE_I.minMembers');
        const error = apiError('Los datos enviados no son válidos.', 400, {
            fields: { 'tracks.INNPRENDE_I.minMembers': 'El mínimo es 1' },
        });

        expect(placeServerErrors(error, setError, control)).toBeNull();
        expect(setError).toHaveBeenCalledWith('tracks.INNPRENDE_I.minMembers', expect.anything(), { shouldFocus: true });
    });

    it('un error sin campos es el error general', () => {
        const { setError, control } = form();

        handleFormError(apiError('Error del servidor. Intenta nuevamente más tarde.', 500), setError, control);

        expect(setError).toHaveBeenCalledTimes(1);
        expect(setError).toHaveBeenCalledWith('root', {
            type: 'server',
            message: 'Error del servidor. Intenta nuevamente más tarde.',
        });
    });
});
