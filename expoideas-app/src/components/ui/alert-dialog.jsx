import { AlertDialog as AlertDialogPrimitive } from 'radix-ui';
import { cn } from '@/lib/utils';
import { buttonVariants } from './button-variants';

/**
 * Confirmación para acciones irreversibles. A diferencia de Dialog, no se
 * cierra al hacer clic fuera: hay que elegir.
 */
export const AlertDialog = AlertDialogPrimitive.Root;

export function AlertDialogContent({ className, children, ...props }) {
    return (
        <AlertDialogPrimitive.Portal>
            <AlertDialogPrimitive.Overlay className="fixed inset-0 z-50 bg-on-surface/40 backdrop-blur-sm data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=closed]:animate-out data-[state=closed]:fade-out-0" />
            <AlertDialogPrimitive.Content
                className={cn(
                    'fixed left-1/2 top-1/2 z-50 grid w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 gap-4',
                    'rounded-lg border border-outline-variant/70 bg-surface-container-lowest p-6 shadow-xl',
                    'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
                    'data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
                    className,
                )}
                {...props}
            >
                {children}
            </AlertDialogPrimitive.Content>
        </AlertDialogPrimitive.Portal>
    );
}

export function AlertDialogTitle({ className, ...props }) {
    return <AlertDialogPrimitive.Title className={cn('font-heading text-lg font-semibold', className)} {...props} />;
}

export function AlertDialogDescription({ className, ...props }) {
    return <AlertDialogPrimitive.Description className={cn('text-sm text-on-surface-variant', className)} {...props} />;
}

export function AlertDialogFooter({ className, ...props }) {
    return <div className={cn('flex flex-col-reverse gap-2 sm:flex-row sm:justify-end', className)} {...props} />;
}

export function AlertDialogCancel({ className, ...props }) {
    return <AlertDialogPrimitive.Cancel className={cn(buttonVariants({ variant: 'outline' }), className)} {...props} />;
}

export function AlertDialogAction({ className, variant = 'destructive', ...props }) {
    return <AlertDialogPrimitive.Action className={cn(buttonVariants({ variant }), className)} {...props} />;
}
