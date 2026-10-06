import { Slot } from 'radix-ui';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { buttonVariants } from './button-variants';

/**
 * Botón con las variantes de Academic Nexus.
 *
 * @param {boolean} [asChild] aplica el estilo al hijo (p. ej. un <Link>) en vez de renderizar <button>
 * @param {boolean} [loading] deshabilita y muestra un spinner
 */
export function Button({ className, variant, size, asChild = false, loading = false, disabled, children, ...props }) {
    const Comp = asChild ? Slot.Root : 'button';

    return (
        <Comp
            data-slot="button"
            className={cn(buttonVariants({ variant, size }), className)}
            disabled={asChild ? undefined : disabled || loading}
            aria-busy={loading || undefined}
            {...props}
        >
            {asChild ? (
                children
            ) : (
                <>
                    {loading && <Loader2 className="animate-spin" aria-hidden="true" />}
                    {children}
                </>
            )}
        </Comp>
    );
}
