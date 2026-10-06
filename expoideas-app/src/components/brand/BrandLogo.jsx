import { cn } from '@/lib/utils';

/*
 * Marca PROVISIONAL de Idearium mientras no exista el logo oficial: monograma
 * "i" en verde institucional con el punto lima (la "idea") y el nombre en la
 * fuente de títulos. Cuando llegue el logo se reemplaza solo este archivo y los
 * iconos de public/ (favicon.svg, apple-touch-icon.png).
 */

const SIZES = {
    sm: { icon: 'size-7', text: 'text-lg' },
    md: { icon: 'size-9', text: 'text-xl' },
};

/** Solo el monograma; mismo dibujo que public/favicon.svg. */
function Monogram({ className }) {
    return (
        <svg viewBox="0 0 32 32" className={className} aria-hidden="true" focusable="false">
            <rect width="32" height="32" rx="7" fill="#006735" />
            <rect x="13.75" y="7" width="4.5" height="4.5" fill="#d9ea3a" />
            <rect x="13.75" y="13.75" width="4.5" height="11.25" fill="#ffffff" />
        </svg>
    );
}

/**
 * Monograma + nombre.
 *
 * @param {'sm'|'md'} [size]
 */
export function BrandLogo({ size = 'md', className }) {
    const sizes = SIZES[size];

    return (
        <span className={cn('inline-flex items-center gap-2.5', className)}>
            <Monogram className={cn('shrink-0', sizes.icon)} />
            <span className={cn('font-heading font-extrabold leading-none tracking-tight text-on-surface', sizes.text)}>
                Idea<span className="text-primary">rium</span>
            </span>
        </span>
    );
}
