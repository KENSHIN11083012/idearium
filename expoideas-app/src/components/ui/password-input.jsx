import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fieldBase } from './styles';

/**
 * Campo de contraseña con botón para mostrarla. Reenvía ref y props al <input>,
 * así que funciona con {...register('password')} y dentro de <Field>.
 */
export function PasswordInput({ className, ref, ...props }) {
    const [visible, setVisible] = useState(false);

    return (
        <div className="relative">
            <input
                ref={ref}
                data-slot="input"
                type={visible ? 'text' : 'password'}
                className={cn(fieldBase, 'h-11 pr-11', className)}
                {...props}
            />
            <button
                type="button"
                onClick={() => setVisible((v) => !v)}
                className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded text-outline hover:text-primary"
                aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                aria-pressed={visible}
            >
                {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
        </div>
    );
}
