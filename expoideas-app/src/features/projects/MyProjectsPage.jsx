import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, FolderPlus, Lightbulb, MailQuestion, Users } from 'lucide-react';
import { toast } from 'sonner';
import { ROUTES } from '@/lib/routes';
import { useAuth } from '@/features/auth/useAuth';
import { trackLabel } from '@/lib/tracks';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { useEditions } from '@/features/editions/queries';
import { useAnswerInvitation, useMyInvitations, useMyProjects } from './queries';
import { ProjectDialog } from './ProjectDialog';

/** Invitación pendiente: quien la recibe acepta o rechaza desde aquí. */
function InvitationCard({ invitation }) {
    const answer = useAnswerInvitation();

    const respond = async (accept) => {
        try {
            await answer.mutateAsync({ id: invitation.id, accept });
            toast.success(accept ? `Ya eres del equipo de "${invitation.projectTitle}"` : 'Invitación rechazada');
        } catch (error) {
            toast.error(error.message);
        }
    };

    return (
        <Card className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-col gap-1">
                <p className="label-mono text-on-surface-variant">
                    {invitation.edition} · {trackLabel(invitation.track)}
                </p>
                <p className="font-semibold text-on-surface">{invitation.projectTitle}</p>
                <p className="text-sm text-on-surface-variant">{invitation.leader} te invitó a su equipo.</p>
            </div>
            <div className="flex gap-2">
                <Button variant="outline" onClick={() => respond(false)} disabled={answer.isPending}>
                    Rechazar
                </Button>
                <Button onClick={() => respond(true)} loading={answer.isPending}>
                    Aceptar
                </Button>
            </div>
        </Card>
    );
}

function ProjectCard({ project }) {
    const accepted = project.members.filter((member) => member.status === 'ACCEPTED').length;

    return (
        <Card className="flex flex-col gap-4 p-6">
            <div className="flex flex-col gap-2">
                <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="outline" mono>
                        {project.edition}
                    </Badge>
                    <Badge variant="lime" mono>
                        {trackLabel(project.track)}
                    </Badge>
                    {!project.registrationOpen && <Badge variant="outline">Inscripción cerrada</Badge>}
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
            </dl>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-outline-variant/50 pt-4">
                <p className="flex items-center gap-2 text-sm text-on-surface-variant">
                    <Users className="size-4 text-primary" aria-hidden="true" />
                    {accepted} de {project.maxMembers} integrantes
                </p>
                <Button variant="outline" size="sm" asChild>
                    <Link to={ROUTES.project(project.id)}>
                        Ver proyecto <ArrowRight />
                    </Link>
                </Button>
            </div>
        </Card>
    );
}

/**
 * Los proyectos de quien tiene la sesión y las invitaciones que le llegaron. Un
 * proyecto se inscribe mientras su edición tenga las inscripciones abiertas.
 */
export default function MyProjectsPage() {
    const { user } = useAuth();
    const { data: projects = [], isPending, error, refetch } = useMyProjects();
    const { data: invitations = [] } = useMyInvitations();
    const { data: editions = [] } = useEditions();
    const [creating, setCreating] = useState(false);

    // Los proyectos en los que ya estoy; los que tengo sin responder salen arriba,
    // en Invitaciones.
    const mine = projects.filter((project) =>
        project.members.some((member) => member.userId === user?.id && member.status === 'ACCEPTED'),
    );
    const registrationOpen = editions.some((edition) => edition.registrationOpen);

    return (
        <PageContainer>
            <PageHeader
                eyebrow="INNPRENDE"
                title="Mis proyectos"
                description="Aquí están los proyectos que inscribiste o en los que participas, y las invitaciones que te hicieron tus compañeros."
                actions={
                    registrationOpen && (
                        <Button onClick={() => setCreating(true)}>
                            <FolderPlus /> Inscribir proyecto
                        </Button>
                    )
                }
            />

            {invitations.length > 0 && (
                <section className="flex flex-col gap-3">
                    <h2 className="flex items-center gap-2 font-heading text-lg font-bold text-on-surface">
                        <MailQuestion className="size-5 text-primary" aria-hidden="true" />
                        Invitaciones
                    </h2>
                    {invitations.map((invitation) => (
                        <InvitationCard key={invitation.id} invitation={invitation} />
                    ))}
                </section>
            )}

            {error ? (
                <ErrorState title="No pudimos cargar tus proyectos" error={error} onRetry={refetch} />
            ) : isPending ? (
                <div className="flex flex-col gap-4" aria-hidden="true">
                    {[0, 1].map((index) => (
                        <Skeleton key={index} className="h-52 rounded" />
                    ))}
                </div>
            ) : mine.length === 0 ? (
                // Con una invitación sin responder el equipo ya está a un clic: no
                // tiene sentido decir que no hay nada ni repetir el botón de arriba.
                <EmptyState
                    icon={Lightbulb}
                    title={invitations.length > 0 ? 'Aún no estás en ningún equipo' : 'Todavía no tienes proyectos'}
                    description={
                        invitations.length > 0
                            ? 'Acepta la invitación que te hicieron o inscribe tu propio proyecto.'
                            : registrationOpen
                              ? 'Inscribe tu proyecto y después invita a tu equipo.'
                              : 'Cuando se abran las inscripciones de una edición podrás inscribir tu proyecto.'
                    }
                    action={
                        registrationOpen &&
                        invitations.length === 0 && (
                            <Button size="sm" onClick={() => setCreating(true)}>
                                <FolderPlus /> Inscribir proyecto
                            </Button>
                        )
                    }
                />
            ) : (
                <div className="flex flex-col gap-4">
                    {mine.map((project) => (
                        <ProjectCard key={project.id} project={project} />
                    ))}
                </div>
            )}

            {creating && <ProjectDialog project={null} onClose={() => setCreating(false)} />}
        </PageContainer>
    );
}
