import { ClipboardCheck } from 'lucide-react';
import { useProjectRubric } from './queries';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { formatGrade, scaleLabel } from './grades';
import { useProjectEvaluations } from './queries';

/** Lo que puso un jurado: su nota y, criterio por criterio, el nivel y la observación. */
function JurorEvaluation({ evaluation, rubric }) {
    const criteria = new Map(rubric.criteria.map((criterion) => [criterion.id, criterion]));
    return (
        <li className="flex flex-col gap-3 rounded border border-outline-variant/70 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium text-on-surface">{evaluation.juror}</p>
                <Badge variant={evaluation.absent ? 'outline' : 'primary'} mono>
                    {evaluation.absent ? 'No asistió' : scaleLabel(evaluation.scale)} · {formatGrade(evaluation.grade)}
                </Badge>
            </div>
            {!evaluation.absent && (
                <dl className="flex flex-col gap-2 text-sm">
                    {evaluation.scores.map((score) => {
                        const criterion = criteria.get(score.criterionId);
                        const level = criterion?.levels.find((candidate) => candidate.id === score.levelId);
                        return (
                            <div key={score.criterionId} className="flex flex-col gap-0.5 sm:flex-row sm:gap-3">
                                <dt className="text-on-surface-variant sm:w-56 sm:shrink-0">
                                    {criterion?.shortName ?? 'Criterio'}
                                </dt>
                                <dd className="flex flex-col gap-0.5 text-on-surface">
                                    <span>
                                        <strong>{formatGrade(score.score)}</strong>
                                        {level && <span className="text-on-surface-variant"> · {level.label}</span>}
                                    </span>
                                    {score.comment && <span className="italic text-on-surface-variant">«{score.comment}»</span>}
                                </dd>
                            </div>
                        );
                    })}
                </dl>
            )}
        </li>
    );
}

/**
 * Cómo va la evaluación de un proyecto, para el profesor del grupo y la
 * gestión: la nota (promedio de los jurados que ya calificaron), el detalle de
 * cada jurado y quién falta. Los jurados y el equipo no la ven.
 */
export function ProjectEvaluations({ project }) {
    const results = useProjectEvaluations(project.id);
    const rubric = useProjectRubric(project.id);

    return (
        <section className="flex flex-col gap-4" aria-labelledby="evaluaciones-titulo">
            <h2 id="evaluaciones-titulo" className="font-heading text-lg font-bold text-on-surface">
                Evaluación
            </h2>
            {results.error || rubric.error ? (
                <ErrorState
                    title="No pudimos cargar la evaluación"
                    error={results.error ?? rubric.error}
                    onRetry={results.error ? results.refetch : rubric.refetch}
                />
            ) : results.isPending || rubric.isPending ? (
                <Skeleton className="h-32 rounded" aria-busy="true" />
            ) : (
                <Card className="flex flex-col gap-5 p-6">
                    <div className="flex flex-wrap items-end justify-between gap-4">
                        <div className="flex flex-col gap-1">
                            <p className="label-mono text-on-surface-variant">Nota del proyecto</p>
                            {results.data.grade === null ? (
                                <p className="text-on-surface-variant">Todavía ningún jurado ha calificado.</p>
                            ) : (
                                <p className="flex items-baseline gap-2">
                                    <strong className="font-heading text-4xl font-bold text-primary">
                                        {formatGrade(results.data.grade)}
                                    </strong>
                                    <span className="text-on-surface">{scaleLabel(results.data.scale)}</span>
                                </p>
                            )}
                        </div>
                        <p className="flex items-center gap-2 text-sm text-on-surface-variant">
                            <ClipboardCheck className="size-4" aria-hidden="true" />
                            {results.data.jurors === 0
                                ? 'Sin jurados asignados'
                                : `${results.data.evaluations.length} de ${results.data.jurors} jurados calificaron`}
                        </p>
                    </div>

                    {results.data.pending.length > 0 && (
                        <p className="text-sm text-on-surface-variant">
                            Falta: {results.data.pending.map((juror) => juror.fullName).join(', ')}.
                        </p>
                    )}

                    {results.data.evaluations.length > 0 && (
                        <ul className="flex flex-col gap-3" aria-label="Evaluaciones de los jurados">
                            {results.data.evaluations.map((evaluation) => (
                                <JurorEvaluation key={evaluation.id} evaluation={evaluation} rubric={rubric.data} />
                            ))}
                        </ul>
                    )}
                </Card>
            )}
        </section>
    );
}
