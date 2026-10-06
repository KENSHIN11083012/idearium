import { Link } from 'react-router-dom';
import { ArrowRight, ClipboardCheck, Gavel } from 'lucide-react';
import { ROUTES } from '@/lib/routes';
import { trackLabel } from '@/lib/tracks';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { formatGrade } from '@/features/evaluations/grades';
import { useMyEvaluations } from '@/features/evaluations/queries';
import { useJuryProjects } from './queries';

/** Si ya lo califiqué, con qué nota; si no, que está pendiente. */
function EvaluationStatus({ evaluation }) {
    if (!evaluation) {
        return (
            <Badge variant="outline" mono>
                Pendiente
            </Badge>
        );
    }
    return (
        <Badge variant="primary" mono>
            {evaluation.absent ? 'No asistió' : 'Calificado'} · {formatGrade(evaluation.grade)}
        </Badge>
    );
}

function ProjectCard({ project, evaluation }) {
    const accepted = project.members.filter((member) => member.status === 'ACCEPTED').length;
    return (
        <Card className="flex flex-col gap-4 p-6">
            <div className="flex flex-col gap-2">
                <div className="flex flex-wrap gap-2">
                    <Badge variant="outline" mono>
                        {project.edition}
                    </Badge>
                    <Badge variant="lime" mono>
                        {trackLabel(project.track)}
                    </Badge>
                    <EvaluationStatus evaluation={evaluation} />
                </div>
                <h2 className="font-heading text-xl font-bold text-on-surface">{project.title}</h2>
                <p className="line-clamp-2 text-sm text-on-surface-variant">{project.summary}</p>
            </div>
            <dl className="flex flex-col gap-1 text-sm text-on-surface-variant sm:flex-row sm:gap-6">
                <div className="flex gap-2">
                    <dt>Sector:</dt>
                    <dd className="font-medium text-on-surface">{project.sector}</dd>
                </div>
                <div className="flex gap-2">
                    <dt>Profesor:</dt>
                    <dd className="font-medium text-on-surface">{project.teacher}</dd>
                </div>
                <div className="flex gap-2">
                    <dt>Equipo:</dt>
                    <dd className="font-medium text-on-surface">
                        {accepted} {accepted === 1 ? 'integrante' : 'integrantes'}
                    </dd>
                </div>
            </dl>
            <div className="flex flex-wrap justify-end gap-2 border-t border-outline-variant/50 pt-4">
                <Button variant="outline" size="sm" asChild>
                    <Link to={ROUTES.project(project.id)}>
                        Ver proyecto <ArrowRight />
                    </Link>
                </Button>
                <Button variant={evaluation ? 'outline' : 'default'} size="sm" asChild>
                    <Link to={ROUTES.evaluate(project.id)}>
                        <ClipboardCheck /> {evaluation ? 'Corregir calificación' : 'Calificar'}
                    </Link>
                </Button>
            </div>
        </Card>
    );
}

/**
 * Los proyectos que a quien tiene la sesión le tocan como jurado. Desde aquí
 * se abre cada ficha, con su equipo y sus entregables, y el tablero para
 * calificarlo con la rúbrica.
 */
export default function JuryProjectsPage() {
    const { data: projects = [], isPending, error, refetch } = useJuryProjects();
    // Si esta consulta falla, los proyectos se siguen viendo: solo falta la marca de cuáles ya están.
    const { data: evaluations = [] } = useMyEvaluations();

    return (
        <PageContainer>
            <PageHeader
                eyebrow="Jurado"
                title="Proyectos por evaluar"
                description="Los proyectos que MacondoLab te asignó. Abre cada uno para ver su equipo y sus entregables, y califícalo con la rúbrica."
            />

            {error ? (
                <ErrorState title="No pudimos cargar tus proyectos" error={error} onRetry={refetch} />
            ) : isPending ? (
                <div className="flex flex-col gap-3" aria-hidden="true">
                    {[0, 1].map((index) => (
                        <Skeleton key={index} className="h-40 rounded" />
                    ))}
                </div>
            ) : projects.length === 0 ? (
                <EmptyState
                    icon={Gavel}
                    title="Todavía no tienes proyectos asignados"
                    description="Cuando MacondoLab te asigne como jurado de un proyecto, aparecerá aquí."
                />
            ) : (
                <div className="flex flex-col gap-4">
                    {projects.map((project) => (
                        <ProjectCard
                            key={project.id}
                            project={project}
                            evaluation={evaluations.find((evaluation) => evaluation.projectId === project.id)}
                        />
                    ))}
                </div>
            )}
        </PageContainer>
    );
}
