import { describe, expect, it } from 'vitest';
import { toQuery } from './query';

describe('toQuery', () => {
    it('arma la cadena con lo que tiene valor', () => {
        expect(toQuery({ search: 'ana maría', role: 'JUDGE', page: 2 })).toBe('?search=ana+mar%C3%ADa&role=JUDGE&page=2');
    });

    it('no envía lo vacío: la API lo entiende como «sin filtrar»', () => {
        expect(toQuery({ search: '', role: null, sectorId: undefined, pendingRole: false })).toBe('');
        expect(toQuery()).toBe('');
    });

    it('la primera página es la 0 y se envía', () => {
        expect(toQuery({ page: 0, size: 25, pendingRole: true })).toBe('?page=0&size=25&pendingRole=true');
    });
});
