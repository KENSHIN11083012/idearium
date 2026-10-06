import { cn } from '@/lib/utils';

/**
 * Clases compartidas por los controles de formulario (input, select y el
 * contenedor del campo de contraseña). Aparte de los componentes para que esos
 * archivos solo exporten componentes (Fast Refresh).
 */
export const fieldBase = cn(
    'w-full rounded border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface',
    'placeholder:text-outline transition-[border-color,box-shadow] duration-150',
    'hover:border-outline',
    'focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20',
    'disabled:cursor-not-allowed disabled:bg-surface-container-low disabled:opacity-70',
    'aria-invalid:border-error aria-invalid:focus-visible:ring-error/20',
);
