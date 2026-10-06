import { describe, expect, it } from 'vitest';
import { average, formatGrade, isFailing, scaleLabel, scaleOf } from './grades';

describe('Escala institucional', () => {
    it.each([
        [0, 'FAILING'],
        [2.9, 'FAILING'],
        [3, 'ACCEPTABLE'],
        [3.9, 'ACCEPTABLE'],
        [4, 'GOOD'],
        [4.4, 'GOOD'],
        [4.5, 'VERY_GOOD'],
        [4.9, 'VERY_GOOD'],
        [5, 'EXCELLENT'],
    ])('%s es %s', (grade, scale) => {
        expect(scaleOf(grade)).toBe(scale);
    });

    it('se aprueba desde 3.0', () => {
        expect(isFailing(2.9)).toBe(true);
        expect(isFailing(3)).toBe(false);
    });

    it('cada nivel tiene su nombre', () => {
        expect(scaleLabel('VERY_GOOD')).toBe('Muy bueno');
        expect(scaleLabel('FAILING')).toBe('Deficiente');
    });
});

describe('Promedio', () => {
    it('es simple, con un decimal, y el medio sube', () => {
        // (4.0 + 4.5) / 2 = 4.25
        expect(average([4, 4.5])).toBe(4.3);
        // (5.0 + 4.5 + 4.0 + 4.5 + 5.0 + 4.0) / 6 = 4.5
        expect(average([5, 4.5, 4, 4.5, 5, 4])).toBe(4.5);
        // (1.5 + 4.5 * 4 + 5.0) / 6 = 4.08
        expect(average([1.5, 4.5, 4.5, 4.5, 4.5, 5])).toBe(4.1);
        // (3.0 + 3.0 + 1.5) / 3 = 2.5
        expect(average([3, 3, 1.5])).toBe(2.5);
    });

    it('sin notas no hay promedio', () => {
        expect(average([])).toBeNull();
    });

    it('las notas se muestran siempre con un decimal', () => {
        expect(formatGrade(4)).toBe('4.0');
        expect(formatGrade(4.5)).toBe('4.5');
    });
});
