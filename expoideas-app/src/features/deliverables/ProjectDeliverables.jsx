import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { CircleCheck, CircleDashed, Download, FileText, Link2, Plus, Trash2, Upload } from 'lucide-react';
import { toast } from 'sonner';
import { downloadFile, validateSize } from '@/lib/files';
import { handleFormError } from '@/lib/validation';
import { formatDay, timeLeft, today } from '@/features/editions/status';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { useProjectDeliverables, useUploadDeliverable } from './queries';
import { KIND_ACCEPT, isLinkKind, kindLabel, linkSchema } from './schemas';

/** Tamaño legible: la API rechaza cualquier cosa por encima de 5 MB. */
const fileSize = (bytes) =>
    bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

/** Cómo se llama una entrega en los mensajes y las etiquetas: por su nombre de archivo o por su dirección. */
const entryName = (entry) => entry.fileName ?? entry.url;

/** Un archivo subido o un enlace registrado. */
function UploadedEntry({ entry, canEdit, onRemove }) {
    const [downloading, setDownloading] = useState(false);
    const isLink = Boolean(entry.url);

    const download = async () => {
        setDownloading(true);
        try {
            await downloadFile(entry.fileId, entry.fileName);
        } catch (error) {
            toast.error(error.message);
        } finally {
            setDownloading(false);
        }
    };

    return (
        <li className="flex items-center justify-between gap-3 rounded border border-outline-variant/60 px-4 py-2.5">
            <div className="flex min-w-0 items-center gap-3">
                {isLink ? (
                    <Link2 className="size-4 shrink-0 text-primary" aria-hidden="true" />
                ) : (
                    <FileText className="size-4 shrink-0 text-primary" aria-hidden="true" />
                )}
                <div className="min-w-0">
                    {isLink ? (
                        <a
                            href={entry.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="block truncate text-sm font-medium text-primary underline-offset-4 hover:underline"
                        >
                            {entry.url}
                        </a>
                    ) : (
                        <p className="truncate text-sm font-medium text-on-surface">{entry.fileName}</p>
                    )}
                    <p className="text-xs text-on-surface-variant">
                        {isLink ? 'registrado' : `${fileSize(entry.sizeBytes)} · subido`} por {entry.uploadedBy}
                    </p>
                </div>
            </div>
            <div className="flex shrink-0 gap-1">
                {!isLink && (
                    <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={download}
                        loading={downloading}
                        aria-label={`Descargar ${entry.fileName}`}
                    >
                        <Download />
                    </Button>
                )}
                {canEdit && (
                    <Button variant="ghost" size="icon-sm" onClick={onRemove} aria-label={`Quitar ${entryName(entry)}`}>
                        <Trash2 />
                    </Button>
                )}
            </div>
        </li>
    );
}

/** El formulario de un enlace: la dirección del video o del prototipo en línea. */
function LinkForm({ type, projectId }) {
    const upload = useUploadDeliverable(projectId);
    const {
        register,
        handleSubmit,
        reset,
        setError,
        control,
        formState: { errors, isSubmitting },
    } = useForm({ resolver: zodResolver(linkSchema), defaultValues: { url: '' } });

    const submit = async ({ url }) => {
        try {
            await upload.mutateAsync({ deliverableTypeId: type.id, url });
            reset();
            toast.success('El enlace se registró');
        } catch (error) {
            handleFormError(error, setError, control);
        }
    };

    return (
        <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-3 sm:flex-row sm:items-start">
            <Field label={`Enlace para ${type.name}`} error={errors.url?.message ?? errors.root?.message} className="flex-1">
                <Input type="url" inputMode="url" placeholder="https://" {...register('url')} />
            </Field>
            {/* Alineado con el campo, no con su etiqueta. */}
            <Button type="submit" variant="outline" size="sm" loading={isSubmitting} className="h-11 sm:mt-6 sm:h-8">
                <Plus /> Agregar enlace
            </Button>
        </form>
    );
}

/** Descarga el formato oficial del entregable, si MacondoLab lo subió. */
function TemplateButton({ type }) {
    const [downloading, setDownloading] = useState(false);

    const download = async () => {
        setDownloading(true);
        try {
            await downloadFile(type.templateFileId, type.templateFileName);
        } catch (error) {
            toast.error(error.message);
        } finally {
            setDownloading(false);
        }
    };

    return (
        <Button variant="outline" size="sm" onClick={download} loading={downloading} className="h-11 sm:h-8">
            <Download /> Descargar plantilla
        </Button>
    );
}

/** Cuánto va la subida en curso: en un celular con mala señal, cinco megas tardan. */
function UploadProgress({ progress }) {
    const percent = Math.round(progress.fraction * 100);
    const label =
        progress.total > 1
            ? `Subiendo "${progress.name}" (${progress.index} de ${progress.total})`
            : `Subiendo "${progress.name}"`;
    return (
        <div className="flex flex-col gap-1.5" aria-live="polite">
            <p className="flex justify-between gap-3 text-xs text-on-surface-variant">
                <span className="truncate">{label}</span>
                <span className="shrink-0 font-mono">{percent} %</span>
            </p>
            <div
                role="progressbar"
                aria-label={label}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={percent}
                className="h-1.5 overflow-hidden rounded-full bg-surface-container-high"
            >
                <div
                    className="h-full rounded-full bg-primary transition-[width] duration-200"
                    style={{ width: `${percent}%` }}
                />
            </div>
        </div>
    );
}

/** Si ese entregable admite cambios hoy: con cierre propio manda ese; si no, el de la edición. */
const isOpenToday = (type, project) => (type.closesOn ? today() <= type.closesOn : project.submissionOpen);

function DeliverableGroup({ group, project, isMember, onRemove }) {
    const { type, files, complete } = group;
    const input = useRef(null);
    const upload = useUploadDeliverable(project.id);
    const isLink = isLinkKind(type.kind);
    const full = files.length >= type.maxFiles;
    const open = isOpenToday(type, project);
    const canEdit = isMember && open;

    // Lo que se está subiendo ahora: { name, index, total, fraction }.
    const [progress, setProgress] = useState(null);
    const room = type.maxFiles - files.length;

    const pick = async (event) => {
        const chosen = [...(event.target.files ?? [])];
        event.target.value = ''; // Permite volver a elegir el mismo archivo.
        if (chosen.length === 0) return;
        if (room <= 0) {
            toast.error(
                type.maxFiles === 1
                    ? 'Solo se acepta un archivo: quita el actual para reemplazarlo.'
                    : 'Ya no caben más archivos: quita uno para subir otro.',
            );
            return;
        }
        if (chosen.length > room) {
            toast.error(
                room === 1
                    ? `Elegiste ${chosen.length} archivos y solo cabe uno más.`
                    : `Elegiste ${chosen.length} archivos y solo caben ${room} más.`,
            );
            return;
        }

        // Lo que la API va a rechazar por tamaño no se envía: en una conexión lenta
        // son minutos de espera para un no.
        const sendable = chosen.filter((file) => {
            const invalid = validateSize(file);
            if (invalid) toast.error(`"${file.name}": ${invalid}`);
            return !invalid;
        });

        let uploaded = 0;
        for (const [index, file] of sendable.entries()) {
            const step = { name: file.name, index: index + 1, total: sendable.length };
            setProgress({ ...step, fraction: 0 });
            try {
                await upload.mutateAsync({
                    deliverableTypeId: type.id,
                    file,
                    onProgress: (fraction) => setProgress({ ...step, fraction }),
                });
                uploaded += 1;
            } catch (error) {
                // Un archivo que falla no frena a los demás.
                toast.error(`"${file.name}": ${error.body?.fields?.file ?? error.message}`);
            }
        }
        setProgress(null);
        if (uploaded === 1) toast.success(sendable.length === 1 ? `"${sendable[0].name}" se subió` : 'Se subió 1 archivo');
        else if (uploaded > 1) toast.success(`Se subieron ${uploaded} archivos`);
    };

    return (
        <Card className="flex flex-col gap-3 p-5">
            <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="flex flex-col gap-1">
                    <p className="flex items-center gap-2 font-semibold text-on-surface">
                        {complete ? (
                            <CircleCheck className="size-4 text-primary" aria-label="Entregado" />
                        ) : (
                            <CircleDashed className="size-4 text-on-surface-variant" aria-label="Pendiente" />
                        )}
                        {type.name}
                    </p>
                    {type.description && <p className="text-sm text-on-surface-variant">{type.description}</p>}
                </div>
                <div className="flex flex-wrap gap-2">
                    <Badge variant={type.required ? 'primary' : 'outline'}>{type.required ? 'Obligatorio' : 'Opcional'}</Badge>
                    <Badge variant="outline">{kindLabel(type.kind)}</Badge>
                    {type.closesOn && (
                        <Badge variant={open ? 'lime' : 'outline'}>
                            {open ? 'Cierra el' : 'Cerró el'} {formatDay(type.closesOn)}
                            {open && timeLeft(type.closesOn) && ` · ${timeLeft(type.closesOn).toLowerCase()}`}
                        </Badge>
                    )}
                </div>
            </div>

            {type.templateFileId && (
                <div className="flex flex-wrap items-center gap-3">
                    <TemplateButton type={type} />
                    <p className="text-xs text-on-surface-variant">Descárgala, diligénciala y súbela aquí.</p>
                </div>
            )}

            {files.length > 0 && (
                <ul className="flex flex-col gap-2">
                    {files.map((entry) => (
                        <UploadedEntry key={entry.id} entry={entry} canEdit={canEdit} onRemove={() => onRemove(entry)} />
                    ))}
                </ul>
            )}

            {isMember && !open && type.closesOn && (
                <p className="text-sm text-on-surface-variant">Este entregable cerró el {formatDay(type.closesOn)}.</p>
            )}

            {canEdit &&
                isLink &&
                (full ? (
                    <p className="text-xs text-on-surface-variant">
                        {type.maxFiles === 1
                            ? 'Solo se acepta un enlace: quita el actual para reemplazarlo.'
                            : `Ya registraron los ${type.maxFiles} enlaces permitidos.`}
                    </p>
                ) : (
                    <LinkForm type={type} projectId={project.id} />
                ))}

            {canEdit && !isLink && (
                <div className="flex flex-wrap items-center gap-3">
                    <input
                        ref={input}
                        type="file"
                        accept={KIND_ACCEPT[type.kind]}
                        // Si caben varios, se eligen de una vez.
                        multiple={room > 1}
                        className="sr-only"
                        aria-label={`Subir archivo para ${type.name}`}
                        onChange={pick}
                    />
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => input.current?.click()}
                        loading={Boolean(progress) || upload.isPending}
                        disabled={full}
                        // Subir es la acción principal del equipo: cómoda de tocar en móvil.
                        className="h-11 sm:h-8"
                    >
                        <Upload /> {room > 1 ? 'Subir archivos' : 'Subir archivo'}
                    </Button>
                    <p className="text-xs text-on-surface-variant">
                        {full
                            ? type.maxFiles === 1
                                ? 'Solo se acepta un archivo: quita el actual para reemplazarlo.'
                                : `Ya subieron los ${type.maxFiles} archivos permitidos.`
                            : `Máximo 5 MB por archivo${type.maxFiles > 1 ? `, hasta ${type.maxFiles}` : ''}.`}
                    </p>
                </div>
            )}

            {progress && <UploadProgress progress={progress} />}
        </Card>
    );
}

/**
 * Los entregables de la cátedra y lo que el equipo lleva subido o registrado.
 * Suben, registran y quitan los integrantes del equipo, hasta el cierre de
 * entregas de la edición o el propio de cada entregable; el profesor y la
 * gestión solo miran y descargan.
 */
export function ProjectDeliverables({ project, isMember }) {
    const { data: groups = [], isPending, error, refetch } = useProjectDeliverables(project.id);
    const remove = useUploadDeliverable(project.id);
    const [confirm, setConfirm] = useState(null);

    const pending = groups.filter((group) => !group.complete).length;

    const removeEntry = async () => {
        try {
            await remove.mutateAsync({ deliverableId: confirm.entry.id });
            toast.success(confirm.entry.url ? 'El enlace se quitó' : 'El archivo se quitó');
        } catch (error) {
            toast.error(error.message);
        } finally {
            setConfirm(null);
        }
    };

    return (
        <section className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="font-heading text-lg font-bold text-on-surface">Entregables</h2>
                {groups.length > 0 && (
                    <p className="label-mono text-on-surface-variant">
                        {pending === 0 ? 'Todo entregado' : `${pending} sin entregar`}
                    </p>
                )}
            </div>

            {!project.submissionOpen && (
                <p className="text-sm text-on-surface-variant">
                    El plazo de entregas de {project.edition} ya cerró. Solo siguen abiertos los entregables con cierre propio.
                </p>
            )}

            {error ? (
                <ErrorState title="No pudimos cargar los entregables" error={error} onRetry={refetch} />
            ) : isPending ? (
                <Skeleton className="h-40 rounded" aria-busy="true" />
            ) : groups.length === 0 ? (
                <EmptyState
                    icon={FileText}
                    title="Esta cátedra todavía no pide entregables"
                    description="Cuando MacondoLab los configure, aparecerán aquí."
                />
            ) : (
                <div className="flex flex-col gap-3">
                    {groups.map((group) => (
                        <DeliverableGroup
                            key={group.type.id}
                            group={group}
                            project={project}
                            isMember={isMember}
                            onRemove={(entry) => setConfirm({ entry })}
                        />
                    ))}
                </div>
            )}

            <ConfirmDialog
                open={Boolean(confirm)}
                title={confirm?.entry?.url ? '¿Quitar el enlace?' : '¿Quitar el archivo?'}
                description={`"${confirm ? entryName(confirm.entry) : ''}" se elimina de la plataforma.`}
                confirmLabel="Quitar"
                onConfirm={removeEntry}
                onClose={() => setConfirm(null)}
            />
        </section>
    );
}
