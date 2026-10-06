import { Award } from 'lucide-react';
import { formatDateTime } from '@/features/presentations/schemas';
import { Card } from '@/components/ui/card';
import { formatGrade, scaleLabel } from './grades';
import { useMyGrade } from './queries';

/**
 * Lo que el equipo ve de su evaluación: la nota final con su nivel y, por
 * criterio, las observaciones de los jurados sin nombres. Solo aparece cuando
 * la gestión publicó las notas de la cátedra y algún jurado calificó; mientras
 * tanto no se muestra nada, ni siquiera que existe.
 */
export function MyGrade({ project }) {
    const { data: grade } = useMyGrade(project.id);
    if (!grade) return null;

    return (
        <section className="flex flex-col gap-4" aria-labelledby="mi-nota-titulo">
            <h2 id="mi-nota-titulo" className="font-heading text-lg font-bold text-on-surface">
                Tu evaluación
            </h2>
            <Card accent="lime" className="flex flex-col gap-5 p-6">
                <div className="flex flex-wrap items-end justify-between gap-4">
                    <div className="flex flex-col gap-1">
                        <p className="label-mono text-on-surface-variant">Nota final</p>
                        <p className="flex items-baseline gap-2">
                            <strong className="font-heading text-4xl font-bold text-primary">{formatGrade(grade.grade)}</strong>
                            <span className="text-on-surface">{scaleLabel(grade.scale)}</span>
                        </p>
                    </div>
                    <p className="flex items-center gap-2 text-sm text-on-surface-variant">
                        <Award className="size-4" aria-hidden="true" />
                        {grade.evaluated === 1 ? 'Calificó 1 jurado' : `Calificaron ${grade.evaluated} jurados`} · publicada el{' '}
                        {formatDateTime(grade.publishedAt)}
                    </p>
                </div>

                {grade.criteria.length === 0 ? (
                    <p className="text-sm text-on-surface-variant">Los jurados no dejaron observaciones.</p>
                ) : (
                    <dl className="flex flex-col gap-3">
                        {grade.criteria.map((criterion) => (
                            <div key={criterion.criterionId} className="flex flex-col gap-1">
                                <dt className="text-sm font-medium text-on-surface">
                                    {criterion.position}. {criterion.name}
                                </dt>
                                {criterion.comments.map((comment, index) => (
                                    <dd key={index} className="text-sm italic text-on-surface-variant">
                                        «{comment}»
                                    </dd>
                                ))}
                            </div>
                        ))}
                    </dl>
                )}
            </Card>
        </section>
    );
}
