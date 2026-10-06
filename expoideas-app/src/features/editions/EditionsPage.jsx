import { useState } from 'react';
import { CalendarClock, CalendarPlus, Eye, EyeOff, FileText, Pencil, Users } from 'lucide-react';
import { toast } from 'sonner';
import { TRACK_DESCRIPTIONS, TRACK_LIST, trackLabel } from '@/lib/tracks';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { useEditions, usePublishGrades } from './queries';
import { editionStatus, formatDay } from './status';
import { DeliverableTypesDialog } from '@/features/deliverables/DeliverableTypesDialog';
import { formatDateTime } from '@/features/presentations/schemas';
import { EditionDialog } from './EditionDialog';

/**
 * Configuración de una cátedra dentro de la tarjeta de la edición, con el
 * estado de las notas: mientras no se publiquen, los equipos no ven la suya.
 */
function TrackCard({ track, settings, onDeliverables, onPublish }) {
    const published = settings?.gradesPublishedAt;
    return (
        <div className="flex flex-col gap-1 rounded border border-outline-variant/60 bg-surface-container-low p-4">
            <p className="label-mono text-on-surface-variant">{trackLabel(track)}</p>
            <p className="text-sm text-on-surface-variant">{TRACK_DESCRIPTIONS[track]}</p>
            <p className="mt-1 flex items-center gap-2 text-sm font-medium text-on-surface">
                <Users className="size-4 text-primary" aria-hidden="true" />
                {settings ? `Grupos de ${settings.minMembers} a ${settings.maxMembers} integrantes` : 'Sin configurar'}
            </p>
            <p className="flex items-center gap-2 text-sm text-on-surface-variant">
                {published ? (
                    <Eye className="size-4 text-primary" aria-hidden="true" />
                ) : (
                    <EyeOff className="size-4" aria-hidden="true" />
                )}
                {published ? `Notas publicadas el ${formatDateTime(published)}` : 'Notas sin publicar: los equipos no las ven'}
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
                <Button variant="outline" size="sm" onClick={onDeliverables}>
                    <FileText /> Entregables
                </Button>
                {settings && (
                    <Button variant={published ? 'ghost' : 'outline'} size="sm" onClick={() => onPublish(track, !published)}>
                        {published ? <EyeOff /> : <Eye />} {published ? 'Ocultar notas' : 'Publicar notas'}
                    </Button>
                )}
            </div>
        </div>
    );
}

function EditionCard({ edition, onEdit }) {
    const status = editionStatus(edition);
    const [deliverablesOf, setDeliverablesOf] = useState(null);
    const [publication, setPublication] = useState(null); // { track, publish }
    const publishGrades = usePublishGrades();

    const confirmPublication = async () => {
        const { track, publish } = publication;
        setPublication(null);
        try {
            await publishGrades.mutateAsync({ id: edition.id, track, publish });
            toast.success(publish ? `Notas de ${trackLabel(track)} publicadas` : `Notas de ${trackLabel(track)} ocultas`);
        } catch (publishError) {
            toast.error(publishError.message);
        }
    };

    return (
        <Card className="flex flex-col gap-5 p-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex flex-col gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                        <h2 className="font-heading text-xl font-bold text-on-surface">{edition.name}</h2>
                        <Badge variant={status.variant}>{status.label}</Badge>
                    </div>
                    <dl className="flex flex-col gap-1 text-sm text-on-surface-variant sm:flex-row sm:gap-6">
                        <div className="flex items-center gap-2">
                            <CalendarClock className="size-4 text-primary" aria-hidden="true" />
                            <dt className="sr-only">Inscripciones</dt>
                            <dd>
                                Inscripciones: {formatDay(edition.registrationOpensOn)} a{' '}
                                {formatDay(edition.registrationClosesOn)}
                            </dd>
                        </div>
                        <div className="flex items-center gap-2">
                            <dt className="sr-only">Cierre de entregas</dt>
                            <dd>Entregas hasta el {formatDay(edition.submissionClosesOn)}</dd>
                        </div>
                    </dl>
                </div>
                <Button variant="outline" size="sm" onClick={() => onEdit(edition)}>
                    <Pencil /> Editar
                </Button>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
                {TRACK_LIST.map((track) => (
                    <TrackCard
                        key={track}
                        track={track}
                        settings={edition.tracks?.find((item) => item.track === track)}
                        onDeliverables={() => setDeliverablesOf(track)}
                        onPublish={(item, publish) => setPublication({ track: item, publish })}
                    />
                ))}
            </div>

            {deliverablesOf && (
                <DeliverableTypesDialog edition={edition} track={deliverablesOf} onClose={() => setDeliverablesOf(null)} />
            )}

            <ConfirmDialog
                open={Boolean(publication)}
                title={publication?.publish ? '¿Publicar las notas?' : '¿Ocultar las notas?'}
                description={
                    publication?.publish
                        ? `Cada equipo de ${trackLabel(publication.track)} en ${edition.name} verá su nota final y las observaciones de los jurados, sin nombres. Los jurados pueden seguir corrigiendo y el equipo verá lo corregido.`
                        : `Los equipos de ${trackLabel(publication?.track)} en ${edition.name} dejarán de ver su nota hasta que la vuelvas a publicar.`
                }
                confirmLabel={publication?.publish ? 'Publicar' : 'Ocultar'}
                onConfirm={confirmPublication}
                onClose={() => setPublication(null)}
            />
        </Card>
    );
}

/**
 * Ediciones de la Expo: cuándo se inscriben los proyectos, hasta cuándo se
 * suben los entregables y de qué tamaño son los grupos en cada cátedra. Las
 * escribe MacondoLab; los límites los define ella, no la plataforma.
 */
export default function EditionsPage() {
    const { data: editions = [], isPending, error, refetch } = useEditions();
    const [dialog, setDialog] = useState(null); // { edition: null | registro }

    const openNew = () => setDialog({ edition: null });

    return (
        <PageContainer>
            <PageHeader
                eyebrow="Gestión"
                title="Ediciones"
                description="Cada edición es una vuelta de Idearium con sus plazos: hasta cuándo se inscriben los proyectos y hasta cuándo se suben los entregables de cada cátedra."
                actions={
                    <Button onClick={openNew}>
                        <CalendarPlus /> Nueva edición
                    </Button>
                }
            />

            {error ? (
                <ErrorState title="No pudimos cargar las ediciones" error={error} onRetry={refetch} />
            ) : isPending ? (
                <div className="flex flex-col gap-4" aria-hidden="true">
                    {[0, 1].map((index) => (
                        <Skeleton key={index} className="h-56 rounded" />
                    ))}
                </div>
            ) : editions.length === 0 ? (
                <EmptyState
                    icon={CalendarClock}
                    title="Todavía no hay ediciones"
                    description="Crea la edición del semestre para que los proyectos puedan inscribirse."
                    action={
                        <Button size="sm" onClick={openNew}>
                            <CalendarPlus /> Nueva edición
                        </Button>
                    }
                />
            ) : (
                <div className="flex flex-col gap-4">
                    {editions.map((edition) => (
                        <EditionCard key={edition.id} edition={edition} onEdit={(item) => setDialog({ edition: item })} />
                    ))}
                </div>
            )}

            {dialog && <EditionDialog edition={dialog.edition} onClose={() => setDialog(null)} />}
        </PageContainer>
    );
}
