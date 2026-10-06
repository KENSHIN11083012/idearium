import { cn } from '@/lib/utils';
import { Eyebrow } from './eyebrow';

/**
 * Encabezado de página: etiqueta técnica, título, descripción y acciones.
 * Fija también el <title> del documento (React 19 lo lleva al <head>).
 */
export function PageHeader({ eyebrow, title, description, actions, className }) {
    return (
        <header
            className={cn(
                'flex flex-col gap-4 border-b border-outline-variant/60 pb-6 sm:flex-row sm:items-end sm:justify-between',
                className,
            )}
        >
            <title>{`${title} · Idearium`}</title>
            <div className="flex flex-col gap-2">
                {eyebrow && <Eyebrow>{eyebrow}</Eyebrow>}
                <h1 className="font-heading text-3xl font-bold tracking-tight text-on-surface sm:text-4xl">{title}</h1>
                {description && <p className="max-w-2xl text-base text-on-surface-variant">{description}</p>}
            </div>
            {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
        </header>
    );
}
