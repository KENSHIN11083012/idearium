import { Dialog as DialogPrimitive } from 'radix-ui';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Diálogo modal accesible (Radix): foco atrapado, Escape para cerrar y
 * aria-labelledby automático con DialogTitle.
 */
export const Dialog = DialogPrimitive.Root;

export function DialogContent({ className, children, ...props }) {
    return (
        <DialogPrimitive.Portal>
            <DialogPrimitive.Overlay
                className={cn(
                    'fixed inset-0 z-50 bg-on-surface/40 backdrop-blur-sm',
                    'data-[state=open]:animate-in data-[state=open]:fade-in-0',
                    'data-[state=closed]:animate-out data-[state=closed]:fade-out-0',
                )}
            />
            <DialogPrimitive.Content
                className={cn(
                    'fixed left-1/2 top-1/2 z-50 flex max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 flex-col',
                    'overflow-hidden rounded-lg border border-outline-variant/70 bg-surface-container-lowest shadow-xl',
                    'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
                    'data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
                    className,
                )}
                {...props}
            >
                {children}
                <DialogPrimitive.Close
                    className="absolute right-2 top-2 flex size-11 items-center justify-center rounded text-outline transition-colors hover:bg-surface-container-low hover:text-on-surface sm:right-4 sm:top-4 sm:size-8"
                    aria-label="Cerrar"
                >
                    <X className="size-4" />
                </DialogPrimitive.Close>
            </DialogPrimitive.Content>
        </DialogPrimitive.Portal>
    );
}

export function DialogHeader({ className, ...props }) {
    return (
        <div
            className={cn('flex shrink-0 flex-col gap-1.5 border-b border-outline-variant/50 px-6 pb-4 pr-16 pt-6', className)}
            {...props}
        />
    );
}

export function DialogTitle({ className, ...props }) {
    return <DialogPrimitive.Title className={cn('font-heading text-lg font-semibold', className)} {...props} />;
}

export function DialogDescription({ className, ...props }) {
    return <DialogPrimitive.Description className={cn('text-sm text-on-surface-variant', className)} {...props} />;
}

/** Lo único que hace scroll cuando el diálogo no cabe: el pie sigue a la vista. */
export function DialogBody({ className, ...props }) {
    return <div className={cn('flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-6 py-5', className)} {...props} />;
}

export function DialogFooter({ className, ...props }) {
    return (
        <div
            className={cn(
                'flex shrink-0 flex-col-reverse gap-2 border-t border-outline-variant/50 bg-surface-container-lowest px-6 py-4 sm:flex-row sm:justify-end',
                className,
            )}
            {...props}
        />
    );
}
