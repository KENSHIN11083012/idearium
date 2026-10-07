import { CalendarClock } from 'lucide-react';
import { cn } from '@/lib/utils';
import { daysUntil, formatDay, timeLeft } from '@/features/editions/status';

/** Desde cuántos días antes del cierre el aviso se resalta. */
const URGENT_DAYS = 3;

/**
 * Cuánto falta para que cierren las entregas de la edición del proyecto. Los
 * últimos días se resalta: es cuando un equipo se queda sin entregar.
 *
 * @param {object} project  el proyecto, con submissionOpen y submissionClosesOn
 */
export function SubmissionDeadline({ project, className }) {
    const closesOn = project.submissionClosesOn;
    if (!closesOn) return null;

    if (!project.submissionOpen) {
        return (
            <p className={cn('text-sm text-on-surface-variant', className)}>Las entregas cerraron el {formatDay(closesOn)}.</p>
        );
    }

    const left = timeLeft(closesOn);
    const urgent = daysUntil(closesOn) <= URGENT_DAYS;
    return (
        <p className={cn('flex items-start gap-2 text-sm', urgent ? 'text-error' : 'text-on-surface-variant', className)}>
            <CalendarClock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>
                {left && (
                    <strong className={urgent ? 'font-semibold' : 'font-semibold text-on-surface'}>{left} para entregar</strong>
                )}
                {left ? ' · cierra el ' : 'Las entregas cierran el '}
                {formatDay(closesOn)}
            </span>
        </p>
    );
}
