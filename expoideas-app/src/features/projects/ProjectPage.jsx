import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CalendarClock, ClipboardCheck, Crown, Pencil, Trash2, X } from 'lucide-react';
import { toast } from 'sonner';
import { blockingError } from '@/lib/queryState';
import { ROUTES } from '@/lib/routes';
import { trackLabel } from '@/lib/tracks';
import { useAuth } from '@/features/auth/useAuth';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { NativeSelect } from '@/components/ui/native-select';
import { ProjectDeliverables } from '@/features/deliverables/ProjectDeliverables';
import { MyGrade } from '@/features/evaluations/MyGrade';
import { ProjectEvaluations } from '@/features/evaluations/ProjectEvaluations';
import { JurorsPanel } from '@/features/jury/JurorsPanel';
import { useJuryProjects } from '@/features/jury/queries';
import { canBeJuror } from '@/features/jury/schemas';
import { useProjectPresentation } from '@/features/presentations/queries';
import { formatDateTime } from '@/features/presentations/schemas';
import { InviteForm } from './InviteForm';
import { useDeleteProject, useProject, useRemoveMember, useSetResult } from './queries';
import { SubmissionDeadline } from './SubmissionDeadline';
import { RESULTS, RESULT_LABELS, resultLabel } from './schemas';
import { ProjectDialog } from './ProjectDialog';

/**
 * El resultado del proyecto. Lo ve todo el mundo; lo cambian el profesor del
 * grupo y la gestión, y solo cuando ya cerraron las entregas. Aprobarlo deja a
 * cada integrante habilitado para la siguiente cátedra.
 */
function ProjectResult({ project, canSet }) {
    const setResult = useSetResult(project.id);
    const [value, setValue] = useState(project.result ?? '');

    const save = async () => {
        try {
            await setResult.mutateAsync(value);
            toast.success(`Resultado guardado: ${RESULT_LABELS[value]}`);
        } catch (error) {
            toast.error(error.message);
        }
    };

    return (
        <div className="flex flex-col gap-2">
            <p className="label-mono text-on-surface-variant">Resultado</p>
            {project.result ? (
                <Badge variant={project.result === RESULTS.APPROVED ? 'primary' : 'outline'} className="self-start">
                    {resultLabel(project.result)}
                </Badge>
            ) : (
                <p className="text-sm text-on-surface-variant">
                    {canSet ? 'Todavía sin registrar.' : 'Se registra cuando cierren las entregas.'}
                </p>
            )}
            {canSet && (
                <div className="flex flex-col gap-2">
                    <NativeSelect
                        aria-label="Resultado del proyecto"
                        value={value}
                        onChange={(event) => setValue(event.target.value)}
                        className="h-9 text-sm"
                    >
                        <option value="">Elige el resultado</option>
                        {Object.values(RESULTS).map((result) => (
                            <option key={result} value={result}>
                                {RESULT_LABELS[result]}
                            </option>
                        ))}
                    </NativeSelect>
                    <Button size="sm" onClick={save} loading={setResult.isPending} disabled={!value || value === project.result}>
                        Guardar resultado
                    </Button>
                    <p className="text-xs text-on-surface-variant">
                        Aprobado habilita a cada integrante para inscribirse en la siguiente cátedra.
                    </p>
                </div>
            )}
        </div>
    );
}

/** La cita de sustentación, cuando la gestión ya la programó. */
function PresentationCard({ projectId }) {
    const { data: presentation } = useProjectPresentation(projectId);
    if (!presentation) return null;
    return (
        <Card className="flex flex-col gap-2 p-6">
            <p className="flex items-center gap-2 label-mono text-on-surface-variant">
                <CalendarClock className="size-4 text-primary" aria-hidden="true" /> Sustentación
            </p>
            <p className="font-medium text-on-surface">{formatDateTime(presentation.startsAt)}</p>
            <p className="text-sm text-on-surface">{presentation.place}</p>
            {presentation.notes && <p className="text-sm text-on-surface-variant">{presentation.notes}</p>}
        </Card>
    );
}

function TeamMember({ member, canRemove, onRemove }) {
    return (
        <li className="flex items-center justify-between gap-3 rounded border border-outline-variant/60 px-4 py-3">
            <div className="flex flex-col gap-0.5">
                <p className="flex items-center gap-2 font-medium text-on-surface">
                    {member.fullName}
                    {member.teamRole === 'LEADER' && (
                        <Badge variant="lime">
                            <Crown className="size-3" aria-hidden="true" /> Líder
                        </Badge>
                    )}
                    {member.status === 'INVITED' && <Badge variant="outline">Invitación enviada</Badge>}
                </p>
                <p className="text-xs text-on-surface-variant">
                    {member.email}
                    {member.semester ? ` · Semestre ${member.semester}` : ''}
                </p>
            </div>
            {canRemove && (
                <Button variant="ghost" size="icon-sm" onClick={onRemove} aria-label={`Quitar a ${member.fullName}`}>
                    <X />
                </Button>
            )}
        </li>
    );
}

/**
 * Un proyecto con su equipo. El líder edita los datos, invita, quita
 * integrantes y puede eliminar la inscripción. Un integrante no se sale ni se
 * cambia de equipo por su cuenta: lo retira el líder o la gestión. Todo eso,
 * mientras la inscripción de la edición siga abierta.
 */
export default function ProjectPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { user, role, isManagement } = useAuth();
    const { data: project, isPending, error: lastError, refetch } = useProject(id);
    // Con la ficha ya en pantalla, un fallo pasajero al volver a consultarla no la quita.
    const error = blockingError({ data: project, error: lastError });
    // Un jurado asignado califica desde aquí; se pregunta solo a los roles que pueden serlo.
    const { data: juryProjects = [] } = useJuryProjects({ enabled: canBeJuror(role) });
    const removeMember = useRemoveMember(id);
    const deleteProject = useDeleteProject();
    const [editing, setEditing] = useState(false);
    const [confirm, setConfirm] = useState(null); // { member } | { project: true }

    if (error) {
        return (
            <PageContainer>
                <ErrorState title="No pudimos cargar el proyecto" error={error} onRetry={refetch} />
            </PageContainer>
        );
    }
    if (isPending) {
        return (
            <PageContainer>
                <Skeleton className="h-72 rounded" aria-busy="true" />
            </PageContainer>
        );
    }

    const me = project.members.find((member) => member.userId === user?.id);
    const isTeacher = project.teacherId === user?.id;
    // El equipo vuelve a Mis proyectos; el profesor y la gestión, al listado; un jurado, a Evaluar.
    const listRoute = me ? ROUTES.MY_PROJECTS : isManagement || isTeacher ? ROUTES.PROJECTS : ROUTES.JURY_PROJECTS;
    const listLabel = me ? 'Mis proyectos' : isManagement || isTeacher ? 'Proyectos' : 'Evaluar';
    const isLeader = me?.teamRole === 'LEADER';
    const isJuror = juryProjects.some((assigned) => assigned.id === project.id);
    const open = project.registrationOpen;
    const accepted = project.members.filter((member) => member.status === 'ACCEPTED').length;
    const full = project.members.length >= project.maxMembers;
    // El resultado se pone tras el cierre de entregas, por el profesor del grupo o la gestión.
    const canSetResult = !project.submissionOpen && (project.teacherId === user?.id || isManagement);

    const removeFromTeam = async (member) => {
        try {
            await removeMember.mutateAsync(member.userId);
            toast.success(`${member.fullName} salió del equipo`);
        } catch (removeError) {
            toast.error(removeError.message);
        } finally {
            setConfirm(null);
        }
    };

    const remove = async () => {
        try {
            await deleteProject.mutateAsync(project.id);
            toast.success('La inscripción se eliminó');
            navigate(ROUTES.MY_PROJECTS);
        } catch (deleteError) {
            toast.error(deleteError.message);
        } finally {
            setConfirm(null);
        }
    };

    return (
        <PageContainer>
            <Button variant="ghost" size="sm" asChild className="self-start">
                <Link to={listRoute}>
                    <ArrowLeft /> {listLabel}
                </Link>
            </Button>

            <PageHeader
                eyebrow={`${project.edition} · ${trackLabel(project.track)}`}
                title={project.title}
                description={project.summary}
                actions={
                    isLeader && open ? (
                        <div className="flex gap-2">
                            <Button variant="outline" onClick={() => setEditing(true)}>
                                <Pencil /> Editar
                            </Button>
                            <Button variant="outline" onClick={() => setConfirm({ project: true })}>
                                <Trash2 /> Eliminar
                            </Button>
                        </div>
                    ) : (
                        isJuror && (
                            <Button asChild>
                                <Link to={ROUTES.evaluate(project.id)}>
                                    <ClipboardCheck /> Calificar
                                </Link>
                            </Button>
                        )
                    )
                }
            />

            {/*
                En escritorio la ficha se va a una columna lateral: el equipo y los
                entregables, que es donde se trabaja, quedan en un ancho legible en
                vez de estirarse por los 1200 px del contenedor. En móvil y tablet
                vuelve a ser una sola columna, con la ficha arriba.
            */}
            <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:gap-8">
                <aside className="flex flex-col gap-4 lg:sticky lg:top-24 lg:col-start-2 lg:row-start-1">
                    <PresentationCard projectId={project.id} />
                    <Card className="flex flex-col gap-4 p-6">
                        <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
                            <div>
                                <dt className="label-mono text-on-surface-variant">Sector</dt>
                                <dd className="text-on-surface">{project.sector}</dd>
                            </div>
                            {project.prototypeType && (
                                <div>
                                    <dt className="label-mono text-on-surface-variant">Tipo de prototipo</dt>
                                    <dd className="text-on-surface">{project.prototypeType}</dd>
                                </div>
                            )}
                            <div>
                                <dt className="label-mono text-on-surface-variant">Profesor del grupo</dt>
                                <dd className="text-on-surface">{project.teacher}</dd>
                            </div>
                            {/* Solo llega a quien puede llamarlo: el líder, su profesor y la gestión. */}
                            {project.leaderPhone && (
                                <div>
                                    <dt className="label-mono text-on-surface-variant">Celular del líder</dt>
                                    <dd className="text-on-surface">{project.leaderPhone}</dd>
                                </div>
                            )}
                        </dl>
                        {!open && (
                            <p className="text-sm text-on-surface-variant">
                                Las inscripciones de {project.edition} ya cerraron: el equipo y los datos quedaron fijos.
                            </p>
                        )}
                        {/* Cerradas las entregas, lo dice la sección de entregables. */}
                        {project.submissionOpen && <SubmissionDeadline project={project} />}
                        {(project.result || !project.submissionOpen) && <ProjectResult project={project} canSet={canSetResult} />}
                    </Card>
                </aside>

                <div className="flex flex-col gap-8 lg:col-start-1 lg:row-start-1">
                    {/* El equipo ve su nota solo cuando la gestión la publicó; hasta entonces no hay panel. */}
                    {me?.status === 'ACCEPTED' && <MyGrade project={project} />}

                    <section className="flex flex-col gap-4">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                            <h2 className="font-heading text-lg font-bold text-on-surface">Equipo</h2>
                            <p className="label-mono text-on-surface-variant">
                                {accepted} de {project.maxMembers} integrantes · mínimo {project.minMembers}
                            </p>
                        </div>

                        <ul className="flex flex-col gap-2">
                            {project.members.map((member) => (
                                <TeamMember
                                    key={member.userId}
                                    member={member}
                                    canRemove={open && member.teamRole !== 'LEADER' && (isLeader || isManagement)}
                                    onRemove={() => setConfirm({ member })}
                                />
                            ))}
                        </ul>

                        {/* Salir o cambiarse de equipo no es cosa de cada quien: pasa por la gestión. */}
                        {open && me?.status === 'ACCEPTED' && !isLeader && (
                            <p className="text-sm text-on-surface-variant">
                                Para salir de este equipo o cambiarte a otro, pídeselo a MacondoLab.
                            </p>
                        )}

                        {isLeader && open && <InviteForm projectId={project.id} disabled={full} />}
                        {isLeader && open && full && (
                            <p className="text-sm text-on-surface-variant">
                                El equipo llegó al máximo de {project.maxMembers} integrantes.
                            </p>
                        )}
                    </section>

                    {/* Quien solo está invitado ve la ficha para decidirse; los entregables, cuando acepte. */}
                    {me && me.status !== 'ACCEPTED' ? (
                        <section className="flex flex-col gap-2">
                            <h2 className="font-heading text-lg font-bold text-on-surface">Entregables</h2>
                            <p className="text-sm text-on-surface-variant">
                                Los verás cuando aceptes la invitación, en Mis proyectos.
                            </p>
                        </section>
                    ) : (
                        <ProjectDeliverables project={project} isMember={me?.status === 'ACCEPTED'} />
                    )}

                    {/* Las notas las ven el profesor del grupo y la gestión; el equipo y los jurados, no. */}
                    {(isManagement || isTeacher) && <ProjectEvaluations project={project} />}

                    {isManagement && <JurorsPanel projectId={project.id} />}
                </div>
            </div>

            {editing && <ProjectDialog project={project} onClose={() => setEditing(false)} />}

            <ConfirmDialog
                open={Boolean(confirm)}
                title={confirm?.project ? '¿Eliminar la inscripción?' : '¿Quitar del equipo?'}
                description={
                    confirm?.project
                        ? `"${project.title}" y su equipo se eliminan. Esta acción no se puede deshacer.`
                        : `${confirm?.member?.fullName ?? ''} dejará de estar en el equipo.`
                }
                confirmLabel={confirm?.project ? 'Eliminar' : 'Quitar'}
                onConfirm={() => (confirm?.project ? remove() : removeFromTeam(confirm.member))}
                onClose={() => setConfirm(null)}
            />
        </PageContainer>
    );
}
