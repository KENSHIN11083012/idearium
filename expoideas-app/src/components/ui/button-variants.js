import { cva } from 'class-variance-authority';

/**
 * Variantes de botón de Academic Nexus. Viven aparte de button.jsx para que ese
 * archivo solo exporte componentes (Fast Refresh); las usan también AlertDialog
 * y los enlaces con <Button asChild>.
 */
export const buttonVariants = cva(
    [
        'inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded font-semibold',
        'transition-[color,background-color,border-color,box-shadow,transform] duration-150',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        'disabled:pointer-events-none disabled:opacity-50',
        "[&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
    ],
    {
        variants: {
            variant: {
                /** Acción principal: verde institucional. */
                default: 'bg-primary text-on-primary hover:bg-primary-container active:translate-y-px',
                /** Acción de innovación: lima con texto oscuro. */
                lime: 'bg-secondary-container text-on-secondary-fixed hover:bg-secondary-fixed-dim active:translate-y-px',
                outline:
                    'border border-outline-variant bg-surface-container-lowest text-on-surface hover:border-primary hover:text-primary',
                ghost: 'text-on-surface hover:bg-surface-container-low hover:text-primary',
                destructive: 'bg-error text-on-error hover:bg-on-error-container active:translate-y-px',
            },
            // En móvil los botones de icono crecen a 44 px, el mínimo cómodo para
            // el dedo; desde sm vuelven a su tamaño compacto.
            size: {
                sm: 'h-8 px-3 text-xs',
                default: 'h-10 px-4 text-sm',
                lg: 'h-12 px-6 text-base',
                icon: 'size-11 sm:size-10',
                'icon-sm': 'size-11 sm:size-8',
            },
        },
        defaultVariants: {
            variant: 'default',
            size: 'default',
        },
    },
);
