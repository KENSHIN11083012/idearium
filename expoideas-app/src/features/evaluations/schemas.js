import { z } from 'zod';
import { isFailing } from './grades';

const COMMENT_REQUIRED = 'Explica la calificación: la observación es obligatoria por debajo de 3.0';

/**
 * El nombre del criterio dentro del formulario. Lleva una letra delante porque
 * react-hook-form toma un nombre numérico por la posición de una lista.
 */
export const fieldOf = (criterionId) => `c${criterionId}`;

/** El nivel elegido (o null) y la observación de un criterio. Espejo de EvaluationService.apply. */
const scoreSchema = (criterion) =>
    z
        .object({
            levelId: z.number().nullable(),
            comment: z.string().trim().max(500, 'Máximo 500 caracteres'),
        })
        .superRefine((score, context) => {
            if (score.levelId === null) {
                context.addIssue({ code: 'custom', path: ['levelId'], message: 'Elige un nivel para este criterio' });
                return;
            }
            const level = criterion.levels.find((candidate) => candidate.id === score.levelId);
            if (level && isFailing(level.score) && score.comment === '') {
                context.addIssue({ code: 'custom', path: ['comment'], message: COMMENT_REQUIRED });
            }
        });

/** Una evaluación completa con esa rúbrica: un nivel por criterio. */
export const evaluationSchema = (rubric) =>
    z.object({
        scores: z.object(Object.fromEntries(rubric.criteria.map((criterion) => [fieldOf(criterion.id), scoreSchema(criterion)]))),
    });

/** Lo que ya se guardó (o nada), como valores del formulario. */
export const formValues = (rubric, evaluation) => ({
    scores: Object.fromEntries(
        rubric.criteria.map((criterion) => {
            const saved = evaluation?.scores.find((score) => score.criterionId === criterion.id);
            return [fieldOf(criterion.id), { levelId: saved?.levelId ?? null, comment: saved?.comment ?? '' }];
        }),
    ),
});

/** Los valores del formulario, como los espera la API (EvaluationRequest). */
export const toRequest = (rubric, values) => ({
    absent: false,
    scores: rubric.criteria.map((criterion) => {
        const { levelId, comment } = values.scores[fieldOf(criterion.id)];
        return { criterionId: criterion.id, levelId, comment: comment.trim() || null };
    }),
});

/**
 * Los errores por criterio de la API («scores.12.comment») con el nombre que
 * tienen en el formulario («scores.c12.comment»).
 */
export const serverFields = (fields = {}) =>
    Object.fromEntries(
        Object.entries(fields).map(([field, message]) => [field.replace(/^scores\.(\d+)\./, 'scores.c$1.'), message]),
    );
