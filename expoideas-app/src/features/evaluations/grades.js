/**
 * Escala de valoración institucional (Art. 62 del Estatuto Estudiantil), de 0.0
 * a 5.0 con un decimal. Espejo de GradeScale.java: si cambia una, cambia la otra.
 */

const SCALE = [
    { value: 'FAILING', label: 'Deficiente', from: 0 },
    { value: 'ACCEPTABLE', label: 'Aceptable', from: 3 },
    { value: 'GOOD', label: 'Bueno', from: 4 },
    { value: 'VERY_GOOD', label: 'Muy bueno', from: 4.5 },
    { value: 'EXCELLENT', label: 'Excelente', from: 5 },
];

const PASSING = 3;

/** El nivel más alto de la escala cuyo mínimo alcanza esa nota. */
export const scaleOf = (grade) => SCALE.findLast((level) => grade >= level.from).value;

/** «Muy bueno» a partir del valor de la API (VERY_GOOD). */
export const scaleLabel = (scale) => SCALE.find((level) => level.value === scale)?.label ?? scale;

/** Por debajo de 3.0 no se aprueba: ahí la observación del jurado deja de ser opcional. */
export const isFailing = (grade) => grade < PASSING;

/**
 * Promedio simple con un decimal (el medio sube), o null si no hay notas. Se
 * hace en décimas enteras para no depender del redondeo de los decimales.
 */
export const average = (grades) => {
    if (grades.length === 0) return null;
    const tenths = grades.reduce((sum, grade) => sum + Math.round(grade * 10), 0);
    return Math.floor((2 * tenths + grades.length) / (2 * grades.length)) / 10;
};

/** 4 -> "4.0": las notas siempre se muestran con un decimal. */
export const formatGrade = (grade) => Number(grade).toFixed(1);
