import { fieldOf } from './schemas';

/**
 * El borrador de una evaluación: lo que el jurado lleva marcado y todavía no
 * guarda. Vive en localStorage, por jurado y por proyecto, para que no se pierda
 * si la página se recarga, la sesión vence o se va la conexión. No se borra al
 * cerrar la sesión: quien vuelve a entrar encuentra lo que dejó.
 */

const keyOf = (owner, projectId) => `expoideas:evaluation-draft:${owner}:${projectId}`;

/** Sobre qué evaluación guardada se hizo el borrador: si después cambió, el borrador ya no aplica. */
const baseOf = (evaluation) => evaluation?.updatedAt ?? null;

/** Lo guardado, con la forma del formulario; null si no encaja con la rúbrica de hoy. */
const valuesFor = (rubric, scores) => {
    const entries = rubric.criteria.map((criterion) => {
        const score = scores?.[fieldOf(criterion.id)];
        const knownLevel = score?.levelId === null || criterion.levels.some((level) => level.id === score?.levelId);
        if (!knownLevel || typeof score.comment !== 'string') return null;
        return [fieldOf(criterion.id), { levelId: score.levelId, comment: score.comment }];
    });
    return entries.includes(null) ? null : { scores: Object.fromEntries(entries) };
};

/** Dos juegos de valores con la misma huella son la misma evaluación. */
export const signatureOf = (rubric, values) =>
    JSON.stringify(
        rubric.criteria.map((criterion) => {
            const score = values?.scores?.[fieldOf(criterion.id)];
            return [score?.levelId ?? null, score?.comment ?? ''];
        }),
    );

/** «20 de noviembre, 10:15 a. m.» */
export const formatDraftTime = (savedAt) =>
    new Date(savedAt).toLocaleString('es-CO', { day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit' });

export const evaluationDraft = {
    /** @returns {{ values: object, savedAt: number } | null} */
    read(owner, projectId, rubric, evaluation) {
        try {
            const stored = JSON.parse(localStorage.getItem(keyOf(owner, projectId)));
            if (!stored) return null;
            const values = stored.base === baseOf(evaluation) ? valuesFor(rubric, stored.scores) : null;
            if (!values) {
                this.clear(owner, projectId);
                return null;
            }
            return { values, savedAt: stored.savedAt };
        } catch {
            return null;
        }
    },

    save(owner, projectId, evaluation, values) {
        try {
            const draft = { base: baseOf(evaluation), savedAt: Date.now(), scores: values.scores };
            localStorage.setItem(keyOf(owner, projectId), JSON.stringify(draft));
        } catch {
            // Sin espacio o sin permiso para guardar: se sigue sin borrador, como antes.
        }
    },

    clear(owner, projectId) {
        try {
            localStorage.removeItem(keyOf(owner, projectId));
        } catch {
            // Nada que borrar.
        }
    },
};
