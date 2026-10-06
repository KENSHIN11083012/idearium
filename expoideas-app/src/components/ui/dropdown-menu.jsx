import { DropdownMenu as DropdownMenuPrimitive } from 'radix-ui';
import { cn } from '@/lib/utils';

/** Menú desplegable accesible (Radix): navegable con teclado y cierre con Escape. */
export const DropdownMenu = DropdownMenuPrimitive.Root;
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;

export function DropdownMenuContent({ className, sideOffset = 6, align = 'end', ...props }) {
    return (
        <DropdownMenuPrimitive.Portal>
            <DropdownMenuPrimitive.Content
                sideOffset={sideOffset}
                align={align}
                className={cn(
                    'z-50 min-w-48 overflow-hidden rounded-lg border border-outline-variant/80 bg-surface-container-lowest p-1.5 shadow-xl',
                    'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
                    'data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
                    'data-[side=bottom]:slide-in-from-top-2 data-[side=top]:slide-in-from-bottom-2',
                    className,
                )}
                {...props}
            />
        </DropdownMenuPrimitive.Portal>
    );
}

export function DropdownMenuItem({ className, variant = 'default', ...props }) {
    return (
        <DropdownMenuPrimitive.Item
            data-variant={variant}
            className={cn(
                'relative flex cursor-pointer select-none items-center gap-2.5 rounded px-2.5 py-2 text-sm outline-none transition-colors',
                'focus:bg-primary/10 focus:text-primary data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
                'data-[variant=destructive]:text-error data-[variant=destructive]:focus:bg-error-container data-[variant=destructive]:focus:text-on-error-container',
                '[&_svg]:size-4 [&_svg]:shrink-0',
                className,
            )}
            {...props}
        />
    );
}

export function DropdownMenuLabel({ className, ...props }) {
    return <DropdownMenuPrimitive.Label className={cn('px-2.5 py-2', className)} {...props} />;
}

export function DropdownMenuSeparator({ className, ...props }) {
    return <DropdownMenuPrimitive.Separator className={cn('-mx-1.5 my-1.5 h-px bg-outline-variant/60', className)} {...props} />;
}
