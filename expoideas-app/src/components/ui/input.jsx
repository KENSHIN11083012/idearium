import { cn } from '@/lib/utils';
import { fieldBase } from './styles';

export function Input({ className, type = 'text', ...props }) {
    return <input data-slot="input" type={type} className={cn(fieldBase, 'h-11', className)} {...props} />;
}
