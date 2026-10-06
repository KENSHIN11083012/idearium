import { z } from 'zod';

/** Qué archivos acepta un entregable. Espejo de DeliverableKind en la API. */
export const KINDS = {
    DOCUMENT: 'DOCUMENT',
    IMAGE: 'IMAGE',
    ANY: 'ANY',
    LINK: 'LINK',
};

const KIND_LABELS = {
    [KINDS.DOCUMENT]: 'PDF',
    [KINDS.IMAGE]: 'Imagen (JPG, PNG o WEBP)',
    [KINDS.ANY]: 'PDF o imagen',
    [KINDS.LINK]: 'Enlace (video, prototipo en línea)',
};

/** Lo que el navegador ofrece en el selector de archivos. */
export const KIND_ACCEPT = {
    [KINDS.DOCUMENT]: 'application/pdf',
    [KINDS.IMAGE]: 'image/jpeg,image/png,image/webp',
    [KINDS.ANY]: 'application/pdf,image/jpeg,image/png,image/webp',
};

export const kindLabel = (kind) => KIND_LABELS[kind] ?? kind;

/** Si el entregable se registra como dirección en vez de subir un archivo. */
export const isLinkKind = (kind) => kind === KINDS.LINK;

/** Entregable de una cátedra (DeliverableTypeRequest). */
export const deliverableTypeSchema = z.object({
    name: z.string().trim().min(1, 'Ingresa el nombre del entregable').max(100, 'Máximo 100 caracteres'),
    description: z.string().trim().max(300, 'Máximo 300 caracteres'),
    kind: z.string().min(1, 'Indica qué archivos se aceptan'),
    required: z.boolean(),
    maxFiles: z
        .string()
        .trim()
        .min(1, 'Ingresa cuántos archivos se aceptan')
        .pipe(z.coerce.number().int('Usa un número entero').min(1, 'Mínimo 1').max(10, 'Máximo 10')),
    /** Cierre propio ("2026-11-28"); vacío usa el de la edición. */
    closesOn: z.string(),
    /** Solo en INNPRENDE II: el tipo de prototipo al que se le pide; vacío, a todos. */
    prototypeTypeId: z.string(),
});

export const toTypeRequest = (values, editionId, track) => ({
    editionId,
    track,
    name: values.name,
    description: values.description || null,
    kind: values.kind,
    required: values.required,
    maxFiles: values.maxFiles,
    sortOrder: values.sortOrder ?? 0,
    closesOn: values.closesOn || null,
    prototypeTypeId: values.prototypeTypeId ? Number(values.prototypeTypeId) : null,
});

export const toTypeFormValues = (type) => ({
    name: type?.name ?? '',
    description: type?.description ?? '',
    kind: type?.kind ?? KINDS.DOCUMENT,
    required: type?.required ?? true,
    maxFiles: type ? String(type.maxFiles) : '1',
    sortOrder: type?.sortOrder ?? 0,
    closesOn: type?.closesOn ?? '',
    prototypeTypeId: type?.prototypeTypeId ? String(type.prototypeTypeId) : '',
});

/** Un enlace para un entregable de tipo LINK. Espejo de DeliverableLinkRequest. */
export const linkSchema = z.object({
    url: z
        .string()
        .trim()
        .min(1, 'Ingresa la dirección del enlace')
        .max(500, 'Máximo 500 caracteres')
        .regex(/^https?:\/\/\S+$/, 'Ingresa una dirección completa, que empiece por http:// o https://'),
});
