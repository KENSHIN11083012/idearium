import { cn } from '@/lib/utils';

/**
 * Etiqueta corta. Las variantes "mono" usan JetBrains Mono en mayúsculas, como
 * los chips técnicos de Academic Nexus.
 */
const VARIANTS = {
    primary: 'bg-primary/10 text-primary border-primary/20',
    lime: 'bg-secondary-container text-on-secondary-fixed border-secondary-fixed-dim',
    dark: 'bg-on-surface text-surface-container-lowest border-on-surface',
    outline: 'bg-surface-container-lowest text-on-surface-variant border-outline-variant',
};

export function Badge({ className, variant = 'primary', mono = false, ...props }) {
    return (
        <span
            data-slot="badge"
            className={cn(
                'inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border px-2.5 py-0.5 text-xs font-semibold',
                mono && 'label-mono',
                VARIANTS[variant],
                className,
            )}
            {...props}
        />
    );
}
