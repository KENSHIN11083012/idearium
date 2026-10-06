import { cn } from '@/lib/utils';
import { fieldBase } from './styles';

/** Texto de varias líneas (propuesta de valor, comentarios). */
export function Textarea({ className, rows = 4, ...props }) {
    return <textarea data-slot="textarea" rows={rows} className={cn(fieldBase, 'min-h-24 py-2.5', className)} {...props} />;
}
