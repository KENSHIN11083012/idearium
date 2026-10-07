import { describe, expect, it } from 'vitest';
import { daysUntil, timeLeft } from './status';

describe('Cuánto falta para un cierre', () => {
    it('cuenta días de calendario: el último día es 0 y lo ya cerrado es negativo', () => {
        expect(daysUntil('2026-11-28', '2026-11-16')).toBe(12);
        expect(daysUntil('2026-11-28', '2026-11-28')).toBe(0);
        expect(daysUntil('2026-11-28', '2026-11-29')).toBe(-1);
    });

    it('no se descuadra al cruzar de mes ni de año', () => {
        expect(daysUntil('2026-12-01', '2026-11-30')).toBe(1);
        expect(daysUntil('2027-01-02', '2026-12-31')).toBe(2);
    });

    it('lo dice en palabras, con el singular y el último día aparte', () => {
        expect(timeLeft('2026-11-28', '2026-11-16')).toBe('Quedan 12 días');
        expect(timeLeft('2026-11-28', '2026-11-27')).toBe('Queda 1 día');
        expect(timeLeft('2026-11-28', '2026-11-28')).toBe('Hoy es el último día');
    });

    it('de un plazo que ya pasó no dice nada', () => {
        expect(timeLeft('2026-11-28', '2026-11-29')).toBeNull();
    });
});
