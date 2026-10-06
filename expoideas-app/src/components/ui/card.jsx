import { cn } from '@/lib/utils';

/**
 * Tarjeta de Academic Nexus: fondo blanco, borde de 1px y radio de 8px.
 * `accent` añade la franja superior de 4px que clasifica el contenido.
 */
const ACCENTS = {
    primary: 'border-t-4 border-t-primary',
    lime: 'border-t-4 border-t-secondary-container',
};

export function Card({ className, accent, ...props }) {
    return (
        <div
            data-slot="card"
            className={cn(
                'rounded-lg border border-outline-variant/70 bg-surface-container-lowest text-on-surface shadow-soft',
                accent && ACCENTS[accent],
                className,
            )}
            {...props}
        />
    );
}

export function CardHeader({ className, ...props }) {
    return <div data-slot="card-header" className={cn('flex flex-col gap-1.5 p-6', className)} {...props} />;
}

export function CardTitle({ className, as: Tag = 'h2', ...props }) {
    return (
        <Tag data-slot="card-title" className={cn('font-heading text-lg font-semibold leading-tight', className)} {...props} />
    );
}

export function CardDescription({ className, ...props }) {
    return <p data-slot="card-description" className={cn('text-sm text-on-surface-variant', className)} {...props} />;
}

export function CardContent({ className, ...props }) {
    return <div data-slot="card-content" className={cn('px-6 pb-6', className)} {...props} />;
}

export function CardFooter({ className, ...props }) {
    return (
        <div
            data-slot="card-footer"
            className={cn('flex items-center gap-3 border-t border-outline-variant/50 px-6 py-4', className)}
            {...props}
        />
    );
}
