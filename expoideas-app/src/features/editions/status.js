/** Estado y fechas de una edición, tal como se muestran. */

/** Hoy en la zona del equipo, en el mismo formato que las fechas de la API ("2026-11-03"). */
export const today = () => new Date().toLocaleDateString('en-CA');

/**
 * En qué punto está la edición. La API ya dice qué se puede hacer hoy
 * (registrationOpen, submissionOpen); las fechas solo distinguen entre lo que
 * todavía no empieza y lo que ya terminó.
 */
export const editionStatus = (edition) => {
    if (edition.registrationOpen) {
        return { label: 'Inscripciones abiertas', variant: 'primary' };
    }
    if (edition.submissionOpen) {
        return { label: 'Solo entregas', variant: 'lime' };
    }
    if (today() < edition.registrationOpensOn) {
        return { label: 'Próxima', variant: 'outline' };
    }
    return { label: 'Cerrada', variant: 'outline' };
};

/** El día como instante en UTC, solo para restar días de calendario sin que la hora ni el cambio de horario estorben. */
const utcDay = (isoDay) => {
    const [year, month, day] = isoDay.split('-').map(Number);
    return Date.UTC(year, month - 1, day);
};

/**
 * Cuántos días faltan para ese día ("2026-11-28"): 0 si es hoy, negativo si ya
 * pasó. Un plazo que cierra un día vale hasta el final de ese día, así que el
 * último día cuenta como 0, no como vencido.
 */
export const daysUntil = (isoDay, from = today()) => Math.round((utcDay(isoDay) - utcDay(from)) / 86_400_000);

/** Lo que falta para un cierre, en palabras; null si ya pasó. */
export const timeLeft = (isoDay, from = today()) => {
    const days = daysUntil(isoDay, from);
    if (days < 0) return null;
    if (days === 0) return 'Hoy es el último día';
    return days === 1 ? 'Queda 1 día' : `Quedan ${days} días`;
};

/** "2026-11-03" -> "3 nov 2026". Sin zona horaria: es un día, no un instante. */
export const formatDay = (isoDay) => {
    if (!isoDay) return '';
    const [year, month, day] = isoDay.split('-').map(Number);
    return new Date(year, month - 1, day).toLocaleDateString('es-CO', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
    });
};
