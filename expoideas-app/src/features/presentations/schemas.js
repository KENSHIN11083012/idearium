import { z } from 'zod';

/** Programar o cambiar una sustentación (PresentationRequest). */
export const presentationSchema = z.object({
    /** "2026-11-20T09:30", como lo entrega un <input type="datetime-local">. */
    startsAt: z.string().min(1, 'Indica la fecha y la hora'),
    place: z.string().trim().min(1, 'Indica el lugar').max(150, 'Máximo 150 caracteres'),
    notes: z.string().trim().max(500, 'Máximo 500 caracteres'),
});

export const toPresentationRequest = (values) => ({
    startsAt: values.startsAt,
    place: values.place,
    notes: values.notes || null,
});

export const toPresentationFormValues = (presentation) => ({
    // La API devuelve segundos ("2026-11-20T09:30:00"); el input quiere minutos.
    startsAt: presentation?.startsAt ? presentation.startsAt.slice(0, 16) : '',
    place: presentation?.place ?? '',
    notes: presentation?.notes ?? '',
});

/** "2026-11-20T09:30:00" -> "viernes, 20 de noviembre de 2026, 9:30 a. m.". Sin zona: es una cita local. */
export const formatDateTime = (iso) => {
    if (!iso) return '';
    const [date, time] = iso.split('T');
    const [year, month, day] = date.split('-').map(Number);
    const [hour, minute] = time.split(':').map(Number);
    return new Date(year, month - 1, day, hour, minute).toLocaleString('es-CO', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
    });
};
