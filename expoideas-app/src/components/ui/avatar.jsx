import { useState } from 'react';
import { initials } from '@/lib/text';
import { cn } from '@/lib/utils';

const SIZES = {
    sm: 'size-8 text-xs',
    md: 'size-10 text-sm',
    lg: 'size-20 text-2xl',
};

/**
 * Foto del usuario o, si no tiene, sus iniciales sobre verde institucional.
 * Si la foto no carga (p. ej. se borró), también muestra las iniciales.
 */
export function Avatar({ firstName, lastName, photoUrl, size = 'md', className }) {
    const name = `${firstName ?? ''} ${lastName ?? ''}`.trim();
    const [failedUrl, setFailedUrl] = useState(null);

    if (photoUrl && photoUrl !== failedUrl) {
        return (
            <img
                src={photoUrl}
                alt={name}
                onError={() => setFailedUrl(photoUrl)}
                className={cn('shrink-0 rounded-full object-cover', SIZES[size], className)}
            />
        );
    }

    return (
        <span
            aria-hidden="true"
            className={cn(
                'flex shrink-0 items-center justify-center rounded-full bg-primary font-heading font-bold text-on-primary',
                SIZES[size],
                className,
            )}
        >
            {initials(firstName, lastName)}
        </span>
    );
}
