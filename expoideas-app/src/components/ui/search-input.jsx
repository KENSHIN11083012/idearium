import { Search } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from './input';

/** Campo de búsqueda con lupa. `label` se usa como nombre accesible. */
export function SearchInput({ label, className, ...props }) {
    return (
        <div className={cn('relative', className)}>
            <Search
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-outline"
                aria-hidden="true"
            />
            <Input type="search" aria-label={label} className="pl-9" {...props} />
        </div>
    );
}
