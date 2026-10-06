import { cn } from '@/lib/utils';
import logoUnisimon from '@/assets/brand/logo-unisimon.png';
import logoMacondoLab from '@/assets/brand/logo-macondolab.png';

/**
 * Logos de la Universidad Simón Bolívar y MacondoLab, lado a lado con un
 * separador, como en la pieza institucional.
 *
 * El verde y el gris de los logos pierden contraste sobre fondos oscuros: ahí
 * hay que darle fondo blanco con className (p. ej. `bg-white px-4 py-3`).
 */
export function InstitutionalLogos({ className }) {
    return (
        <div className={cn('inline-flex items-center gap-3 rounded-lg', className)}>
            <img src={logoUnisimon} alt="Universidad Simón Bolívar" className="h-9 w-auto object-contain" />
            <span className="h-7 w-px bg-outline-variant" aria-hidden="true" />
            <img
                src={logoMacondoLab}
                alt="MacondoLab, Centro de Crecimiento Empresarial e Innovación"
                className="h-9 w-auto object-contain"
            />
        </div>
    );
}
