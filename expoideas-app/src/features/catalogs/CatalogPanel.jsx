import { useDeferredValue, useMemo, useState } from 'react';
import { ArrowDownAZ, ArrowUpAZ, FolderOpen, Pencil, Plus, SearchX } from 'lucide-react';
import { normalizeText } from '@/lib/text';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { SearchInput } from '@/components/ui/search-input';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { useCatalogItems } from './queries';
import { CatalogDialog } from './CatalogDialog';

/**
 * Contenido de una pestaña de Catálogos: búsqueda, orden, tabla y diálogo de
 * alta o edición.
 */
export function CatalogPanel({ catalog }) {
    const { data: items = [], isPending, error, refetch } = useCatalogItems(catalog.path);

    const [search, setSearch] = useState('');
    const [ascending, setAscending] = useState(true);
    const [dialog, setDialog] = useState(null); // { item: null | registro }
    const deferredSearch = useDeferredValue(search);

    const visible = useMemo(() => {
        const term = normalizeText(deferredSearch.trim());
        return items
            .filter((item) => !term || normalizeText(`${item.name} ${item.faculty ?? ''}`).includes(term))
            .sort((a, b) => (ascending ? 1 : -1) * a.name.localeCompare(b.name, 'es'));
    }, [items, deferredSearch, ascending]);

    const openNew = () => setDialog({ item: null });

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                <SearchInput
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder={`Buscar en ${catalog.label.toLowerCase()}`}
                    label={`Buscar en ${catalog.label}`}
                    className="flex-1"
                />
                <div className="flex gap-2">
                    <Button
                        variant="outline"
                        onClick={() => setAscending((value) => !value)}
                        aria-label={ascending ? 'Ordenar de Z a A' : 'Ordenar de A a Z'}
                    >
                        {ascending ? <ArrowDownAZ /> : <ArrowUpAZ />}
                        <span className="sm:hidden lg:inline">{ascending ? 'A–Z' : 'Z–A'}</span>
                    </Button>
                    <Button onClick={openNew} className="flex-1 sm:flex-none">
                        <Plus /> Agregar
                    </Button>
                </div>
            </div>

            {error ? (
                <ErrorState title={`No pudimos cargar ${catalog.label.toLowerCase()}`} error={error} onRetry={refetch} />
            ) : isPending ? (
                <div className="flex flex-col gap-2" aria-hidden="true">
                    {[0, 1, 2, 3, 4].map((index) => (
                        <Skeleton key={index} className="h-12 rounded" />
                    ))}
                </div>
            ) : visible.length === 0 ? (
                <EmptyState
                    icon={search ? SearchX : FolderOpen}
                    title={search ? 'Sin resultados' : 'Todavía no hay registros'}
                    description={search ? `Nada coincide con "${search}".` : `Agrega ${catalog.article} para empezar.`}
                    action={
                        !search && (
                            <Button size="sm" onClick={openNew}>
                                <Plus /> Agregar
                            </Button>
                        )
                    }
                />
            ) : (
                <Card className="overflow-hidden">
                    <table className="w-full text-left text-sm">
                        <thead className="border-b border-outline-variant/60 bg-surface-container-low">
                            <tr className="label-mono text-on-surface-variant">
                                <th scope="col" className="w-20 px-5 py-3">
                                    ID
                                </th>
                                <th scope="col" className="px-5 py-3">
                                    Nombre
                                </th>
                                {catalog.hasFaculty && (
                                    <th scope="col" className="hidden px-5 py-3 sm:table-cell">
                                        Facultad
                                    </th>
                                )}
                                <th scope="col" className="w-16 px-5 py-3">
                                    <span className="sr-only">Acciones</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-outline-variant/50">
                            {visible.map((item) => (
                                <tr key={item.id} className="transition-colors hover:bg-surface-container-low/60">
                                    <td className="px-5 py-3">
                                        <Badge variant="outline" mono>
                                            {item.id}
                                        </Badge>
                                    </td>
                                    <td className="px-5 py-3">
                                        <p className="font-medium text-on-surface">{item.name}</p>
                                        {catalog.hasFaculty && (
                                            <p className="text-xs text-on-surface-variant sm:hidden">{item.faculty}</p>
                                        )}
                                    </td>
                                    {catalog.hasFaculty && (
                                        <td className="hidden px-5 py-3 text-on-surface-variant sm:table-cell">{item.faculty}</td>
                                    )}
                                    <td className="px-5 py-2 text-right">
                                        <Button
                                            variant="ghost"
                                            size="icon-sm"
                                            onClick={() => setDialog({ item })}
                                            aria-label={`Editar ${item.name}`}
                                        >
                                            <Pencil />
                                        </Button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    <p
                        className="label-mono border-t border-outline-variant/50 px-5 py-3 text-on-surface-variant"
                        aria-live="polite"
                    >
                        {visible.length === items.length
                            ? `${items.length} ${items.length === 1 ? 'registro' : 'registros'}`
                            : `${visible.length} de ${items.length} registros`}
                    </p>
                </Card>
            )}

            {dialog && <CatalogDialog catalog={catalog} item={dialog.item} onClose={() => setDialog(null)} />}
        </div>
    );
}
