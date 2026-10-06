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
