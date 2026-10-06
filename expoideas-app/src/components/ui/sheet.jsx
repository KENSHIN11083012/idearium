import { Dialog as DialogPrimitive } from 'radix-ui';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Panel lateral (el menú en móvil y en tablet). Es un Dialog de Radix que entra
 * desde el borde: atrapa el foco y se cierra con Escape o tocando fuera.
 */
export const Sheet = DialogPrimitive.Root;
export const SheetTrigger = DialogPrimitive.Trigger;
export const SheetTitle = DialogPrimitive.Title;
export const SheetDescription = DialogPrimitive.Description;

export function SheetContent({ className, children, ...props }) {
    return (
        <DialogPrimitive.Portal>
            <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-on-surface/40 backdrop-blur-sm data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=closed]:animate-out data-[state=closed]:fade-out-0" />
            <DialogPrimitive.Content
                className={cn(
                    'fixed inset-y-0 right-0 z-50 flex h-full w-[min(20rem,85vw)] flex-col border-l border-outline-variant/70 bg-surface-container-lowest shadow-xl',
                    'data-[state=open]:animate-in data-[state=open]:slide-in-from-right data-[state=open]:duration-300',
                    'data-[state=closed]:animate-out data-[state=closed]:slide-out-to-right data-[state=closed]:duration-200',
                    className,
                )}
                {...props}
            >
                {children}
                <DialogPrimitive.Close
                    className="absolute right-3 top-4 rounded p-2 text-outline transition-colors hover:bg-surface-container-low hover:text-on-surface"
                    aria-label="Cerrar menú"
                >
                    <X className="size-5" />
                </DialogPrimitive.Close>
            </DialogPrimitive.Content>
        </DialogPrimitive.Portal>
    );
}
