import { useEffect, useRef, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Download, FileText, Pencil, Plus, Trash2, Upload, X } from 'lucide-react';
import { toast } from 'sonner';
import { TEMPLATE_ACCEPT, downloadFile, validateSize } from '@/lib/files';
import { trackLabel } from '@/lib/tracks';
import { handleFormError } from '@/lib/validation';
import { CATALOG_PATHS } from '@/features/catalogs/api';
import { useCatalogItems } from '@/features/catalogs/queries';
import { formatDay } from '@/features/editions/status';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog';
import { Alert, EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { NativeSelect } from '@/components/ui/native-select';
import { Textarea } from '@/components/ui/textarea';
import { useDiscardGuard } from '@/components/forms/useDiscardGuard';
import { useDeliverableTemplate, useDeliverableTypes, useSaveDeliverableType } from './queries';
import { KINDS, deliverableTypeSchema, isLinkKind, kindLabel, toTypeFormValues, toTypeRequest } from './schemas';

/**
 * Alta y edición de un entregable, dentro del mismo diálogo que la lista.
 *
 * @param {(dirty: boolean) => void} onDirtyChange avisa si hay algo escrito sin guardar
 */
function TypeForm({ type, editionId, track, onDone, onCancel, onDirtyChange }) {
    const save = useSaveDeliverableType(editionId, track);
    const {
        register,
        control,
        handleSubmit,
        setError,
        formState: { errors, isSubmitting, isDirty },
    } = useForm({ resolver: zodResolver(deliverableTypeSchema), defaultValues: toTypeFormValues(type) });

    useEffect(() => {
        onDirtyChange(isDirty);
        return () => onDirtyChange(false);
    }, [isDirty, onDirtyChange]);
    const isLink = isLinkKind(useWatch({ control, name: 'kind' }));
    // Solo INNPRENDE II distingue entregables por tipo de prototipo, y solo si el catálogo tiene tipos.
    const { data: prototypeTypes = [] } = useCatalogItems(CATALOG_PATHS.prototypeTypes);
    const asksPrototypeType = track === 'INNPRENDE_II' && prototypeTypes.length > 0;

    const submit = async (values) => {
        try {
            await save.mutateAsync({ id: type?.id, body: toTypeRequest(values, editionId, track) });
            toast.success(type ? 'El entregable se actualizó' : `"${values.name}" se agregó`);
            onDone();
        } catch (error) {
            if (error.status === 409) {
                setError('name', { type: 'server', message: 'Ya hay un entregable con ese nombre' }, { shouldFocus: true });
            } else {
                handleFormError(error, setError, control);
            }
        }
    };

    return (
        <form
            onSubmit={handleSubmit(submit)}
            noValidate
            className="flex flex-col gap-4 rounded border border-outline-variant/60 bg-surface-container-low p-4"
        >
            {errors.root && <Alert variant="error" title={errors.root.message} />}
            <Field label="Nombre" error={errors.name?.message} required>
                <Input autoComplete="off" autoFocus {...register('name')} />
            </Field>
            <Field
                label="Descripción"
                error={errors.description?.message}
                hint="Qué se espera y en qué condiciones. Lo lee el equipo antes de subir."
            >
                <Textarea rows={2} {...register('description')} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Archivos aceptados" error={errors.kind?.message} required>
                    <NativeSelect {...register('kind')}>
                        {Object.values(KINDS).map((kind) => (
                            <option key={kind} value={kind}>
                                {kindLabel(kind)}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
                <Field label={isLink ? 'Máximo de enlaces' : 'Máximo de archivos'} error={errors.maxFiles?.message} required>
                    <Input type="number" min="1" max="10" {...register('maxFiles')} />
                </Field>
            </div>
            {asksPrototypeType && (
                <Field
                    label="Aplica a"
                    error={errors.prototypeTypeId?.message}
                    hint="Un entregable con tipo solo se pide a los proyectos de ese tipo de prototipo."
                >
                    <NativeSelect {...register('prototypeTypeId')}>
                        <option value="">Todos los tipos de prototipo</option>
                        {prototypeTypes.map((type) => (
                            <option key={type.id} value={String(type.id)}>
                                Solo {type.name}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
            )}
            <Field
                label="Cierre propio"
                error={errors.closesOn?.message}
                hint="Déjalo vacío para usar el cierre de entregas de la edición. Sirve para lo que se entrega después de la sustentación, como las fotos."
            >
                <Input type="date" {...register('closesOn')} />
            </Field>
            <Controller
                control={control}
                name="required"
                render={({ field }) => (
                    <div className="flex items-center gap-3">
                        <Checkbox
                            id="deliverable-required"
                            checked={field.value}
                            onCheckedChange={(checked) => field.onChange(checked === true)}
                            onBlur={field.onBlur}
                            ref={field.ref}
                        />
                        <label htmlFor="deliverable-required" className="text-sm text-on-surface">
                            Es obligatorio: sin él, el proyecto queda incompleto
                        </label>
                    </div>
                )}
            />
            <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" size="sm" onClick={onCancel}>
                    Cancelar
                </Button>
                <Button type="submit" size="sm" loading={isSubmitting}>
                    {type ? 'Guardar cambios' : 'Agregar'}
                </Button>
            </div>
        </form>
    );
}

/**
 * La plantilla de un entregable ya guardado: el formato oficial (PDF, DOCX o
 * PPTX) que el equipo descarga y diligencia. Los enlaces no llevan plantilla.
 */
function TemplateControls({ type, editionId, track }) {
    const input = useRef(null);
    const template = useDeliverableTemplate(editionId, track);
    const [downloading, setDownloading] = useState(false);

    const pick = async (event) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) return;
        const invalid = validateSize(file);
        if (invalid) {
            toast.error(invalid);
            return;
        }
        try {
            await template.mutateAsync({ id: type.id, file });
            toast.success(`La plantilla de "${type.name}" se guardó`);
        } catch (error) {
            toast.error(error.body?.fields?.file ?? error.message);
        }
    };

    const remove = async () => {
        try {
            await template.mutateAsync({ id: type.id });
            toast.success(`La plantilla de "${type.name}" se quitó`);
        } catch (error) {
            toast.error(error.message);
        }
    };

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
        <div className="flex flex-wrap items-center gap-2 text-sm">
            <input
                ref={input}
                type="file"
                accept={TEMPLATE_ACCEPT}
                className="sr-only"
                aria-label={`Subir plantilla para ${type.name}`}
                onChange={pick}
            />
            {type.templateFileId ? (
                <>
                    <span className="text-on-surface-variant">Plantilla:</span>
                    <Button variant="ghost" size="sm" onClick={download} loading={downloading}>
                        <Download /> {type.templateFileName}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => input.current?.click()} loading={template.isPending}>
                        <Upload /> Reemplazar
                    </Button>
                    <Button variant="ghost" size="sm" onClick={remove} aria-label={`Quitar plantilla de ${type.name}`}>
                        <X /> Quitar
                    </Button>
                </>
            ) : (
                <Button variant="ghost" size="sm" onClick={() => input.current?.click()} loading={template.isPending}>
                    <Upload /> Subir plantilla
                </Button>
            )}
        </div>
    );
}

function TypeRow({ type, editionId, track, onEdit, onDelete }) {
    return (
        <li className="flex items-start justify-between gap-3 rounded border border-outline-variant/60 px-4 py-3">
            <div className="flex min-w-0 flex-col gap-1">
                <p className="font-medium text-on-surface">{type.name}</p>
                {type.description && <p className="text-sm text-on-surface-variant">{type.description}</p>}
                <div className="flex flex-wrap gap-2">
                    <Badge variant={type.required ? 'primary' : 'outline'}>{type.required ? 'Obligatorio' : 'Opcional'}</Badge>
                    <Badge variant="outline">{kindLabel(type.kind)}</Badge>
                    <Badge variant="outline">
                        {isLinkKind(type.kind)
                            ? type.maxFiles === 1
                                ? 'Un enlace'
                                : `Hasta ${type.maxFiles} enlaces`
                            : type.maxFiles === 1
                              ? 'Un archivo'
                              : `Hasta ${type.maxFiles} archivos`}
                    </Badge>
                    {type.closesOn && <Badge variant="lime">Cierra el {formatDay(type.closesOn)}</Badge>}
                    {type.prototypeType && <Badge variant="dark">Solo {type.prototypeType}</Badge>}
                </div>
                {!isLinkKind(type.kind) && <TemplateControls type={type} editionId={editionId} track={track} />}
            </div>
            <div className="flex shrink-0 gap-1">
                <Button variant="ghost" size="icon-sm" onClick={onEdit} aria-label={`Editar ${type.name}`}>
                    <Pencil />
                </Button>
                <Button variant="ghost" size="icon-sm" onClick={onDelete} aria-label={`Eliminar ${type.name}`}>
                    <Trash2 />
                </Button>
            </div>
        </li>
    );
}

/**
 * Los entregables que pide una cátedra en una edición: el póster, las fotos del
 * prototipo, las evidencias de validación, el video del pitch. Los define
 * MacondoLab y son datos, no código: cambiarlos no necesita un despliegue nuevo.
 */
export function DeliverableTypesDialog({ edition, track, onClose }) {
    const { data: types = [], isPending, error, refetch } = useDeliverableTypes(edition.id, track);
    const save = useSaveDeliverableType(edition.id, track);
    const [form, setForm] = useState(null); // { type: null | registro }
    const [formDirty, setFormDirty] = useState(false);
    const [confirm, setConfirm] = useState(null);
    // Cerrar el diálogo entero, o solo el formulario, con algo escrito pregunta antes.
    const closing = useDiscardGuard(formDirty, onClose);
    const cancelling = useDiscardGuard(formDirty, () => setForm(null));

    const remove = async () => {
        try {
            await save.mutateAsync({ id: confirm.id, remove: true });
            toast.success(`"${confirm.name}" se eliminó`);
        } catch (deleteError) {
            toast.error(deleteError.message);
        } finally {
            setConfirm(null);
        }
    };

    return (
        <Dialog open onOpenChange={(open) => !open && closing.requestClose()}>
            <DialogContent className="sm:max-w-2xl">
                <DialogHeader>
                    <DialogTitle>Entregables de {trackLabel(track)}</DialogTitle>
                    <DialogDescription>Lo que los proyectos de esta cátedra deben subir en {edition.name}.</DialogDescription>
                </DialogHeader>
                <DialogBody className="flex flex-col gap-4">
                    {error ? (
                        <ErrorState title="No pudimos cargar los entregables" error={error} onRetry={refetch} />
                    ) : isPending ? (
                        <Skeleton className="h-32" aria-busy="true" />
                    ) : types.length === 0 && !form ? (
                        <EmptyState
                            icon={FileText}
                            title="Esta cátedra todavía no pide entregables"
                            description="Agrega el primero para que los equipos sepan qué subir."
                        />
                    ) : (
                        <ul className="flex flex-col gap-2">
                            {types.map((type) => (
                                <TypeRow
                                    key={type.id}
                                    type={type}
                                    editionId={edition.id}
                                    track={track}
                                    onEdit={() => setForm({ type })}
                                    onDelete={() => setConfirm(type)}
                                />
                            ))}
                        </ul>
                    )}

                    {form ? (
                        <TypeForm
                            type={form.type}
                            editionId={edition.id}
                            track={track}
                            onDone={() => setForm(null)}
                            onCancel={cancelling.requestClose}
                            onDirtyChange={setFormDirty}
                        />
                    ) : (
                        <Button variant="outline" size="sm" className="self-start" onClick={() => setForm({ type: null })}>
                            <Plus /> Agregar entregable
                        </Button>
                    )}
                </DialogBody>
                <DialogFooter>
                    <Button type="button" onClick={closing.requestClose}>
                        Listo
                    </Button>
                </DialogFooter>
            </DialogContent>
            {closing.discardDialog}
            {cancelling.discardDialog}

            <ConfirmDialog
                open={Boolean(confirm)}
                title="¿Eliminar el entregable?"
                description={`"${confirm?.name ?? ''}" dejará de pedirse en esta cátedra.`}
                confirmLabel="Eliminar"
                onConfirm={remove}
                onClose={() => setConfirm(null)}
            />
        </Dialog>
    );
}
