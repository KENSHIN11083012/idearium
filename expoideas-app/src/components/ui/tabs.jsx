import { Tabs as TabsPrimitive } from 'radix-ui';
import { cn } from '@/lib/utils';

/** Pestañas accesibles (Radix). La activa se marca con la barra lima de Academic Nexus. */
export const Tabs = TabsPrimitive.Root;

export function TabsList({ className, ...props }) {
    return (
        <TabsPrimitive.List
            className={cn('scrollbar-none flex w-full gap-1 overflow-x-auto border-b border-outline-variant/70', className)}
            {...props}
        />
    );
}

export function TabsTrigger({ className, ...props }) {
    return (
        <TabsPrimitive.Trigger
            className={cn(
                'relative -mb-px inline-flex shrink-0 items-center gap-2 whitespace-nowrap border-b-[3px] border-transparent px-4 py-3 text-sm font-semibold text-on-surface-variant transition-colors',
                'hover:text-primary data-[state=active]:border-secondary-container data-[state=active]:text-primary',
                'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
                className,
            )}
            {...props}
        />
    );
}

export function TabsContent({ className, ...props }) {
    return <TabsPrimitive.Content className={cn('pt-6 outline-none', className)} {...props} />;
}
