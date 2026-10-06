import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fieldBase } from './styles';

/**
 * <select> nativo con el estilo de los campos. Nativo a propósito: en celular
 * abre el selector del sistema y es accesible sin trabajo extra.
 */
export function NativeSelect({ className, children, ref, ...props }) {
    return (
        <div className="relative">
            <select ref={ref} data-slot="select" className={cn(fieldBase, 'h-11 appearance-none pr-9', className)} {...props}>
                {children}
            </select>
            <ChevronDown
                className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-outline"
                aria-hidden="true"
            />
        </div>
    );
}
