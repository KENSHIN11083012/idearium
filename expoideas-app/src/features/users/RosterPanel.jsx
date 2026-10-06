import { useDeferredValue, useMemo, useRef, useState } from 'react';
import { Download, FileUp, ListChecks, Trash2, X } from 'lucide-react';
import { toast } from 'sonner';
import { roleLabel } from '@/lib/roles';
import { fullName, normalizeText } from '@/lib/text';
import { validateSize } from '@/lib/files';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Alert, EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { SearchInput } from '@/components/ui/search-input';
import { useClearRoster, useImportRoster, useRemoveRosterEntry, useRoster } from './queries';

/** Un CSV de ejemplo con las columnas que lee la API, para rellenarlo en Excel. */
const TEMPLATE =
    '﻿correo;rol;nombres;apellidos\nana.perez@unisimon.edu.co;estudiante;Ana María;Pérez\ncarlos.mendoza@unisimon.edu.co;profesor;Carlos;Mendoza\n';

const downloadTemplate = () => {
    const url = URL.createObjectURL(new Blob([TEMPLATE], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'listado-catedra.csv';
    link.click();
    URL.revokeObjectURL(url);
};

/** Qué pasó con la última carga: cuántos entraron y qué filas no. */
function ImportSummary({ result, onClose }) {
    const rejected = result.rejected.length;
    return (
        <Alert
            variant={rejected === 0 ? 'success' : 'info'}
            title={`${result.added} ${result.added === 1 ? 'persona nueva' : 'personas nuevas'}, ${result.updated} ${result.updated === 1 ? 'actualizada' : 'actualizadas'}${rejected ? `, ${rejected} ${rejected === 1 ? 'fila rechazada' : 'filas rechazadas'}` : ''}`}
        >
            <p>
                El listado tiene ahora {result.total} {result.total === 1 ? 'persona' : 'personas'}
                {result.registered > 0
                    ? `; ${result.registered} de las cargadas ya ${result.registered === 1 ? 'tiene' : 'tienen'} cuenta`
                    : ''}
                .
            </p>
            {rejected > 0 && (
                <ul className="mt-2 flex flex-col gap-0.5" aria-label="Filas rechazadas">
                    {result.rejected.map((row) => (
                        <li key={row.line}>
                            Línea {row.line}
                            {row.email ? ` (${row.email})` : ''}: {row.reason}
                        </li>
                    ))}
                </ul>
            )}
            <Button variant="ghost" size="sm" className="mt-2" onClick={onClose}>
                Entendido
            </Button>
        </Alert>
    );
}

/** La misma fila en celular, donde la tabla no cabe. */
function EntryCard({ entry, onRemove, removing }) {
    return (
        <li>
            <Card className="flex items-start justify-between gap-3 p-4">
                <div className="flex min-w-0 flex-col gap-1">
                    <p className="font-medium text-on-surface">
                        {entry.firstName || entry.lastName ? fullName(entry.firstName, entry.lastName) : '—'}
                    </p>
                    <p className="truncate text-xs text-on-surface-variant">{entry.email}</p>
                    <div className="flex flex-wrap gap-2 pt-1">
                        <Badge variant="outline">{roleLabel(entry.role)}</Badge>
                        <Badge variant={entry.registered ? 'primary' : 'outline'}>
                            {entry.registered ? 'Registrado' : 'Sin registrar'}
                        </Badge>
                    </div>
                </div>
                <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => onRemove(entry)}
                    disabled={removing}
                    aria-label={`Quitar del listado a ${entry.email}`}
                >
                    <X />
                </Button>
            </Card>
        </li>
    );
}

function Row({ entry, onRemove, removing }) {
    return (
        <tr className="transition-colors hover:bg-surface-container-low/60">
            <td className="px-5 py-3">
                <p className="font-medium text-on-surface">
                    {entry.firstName || entry.lastName ? fullName(entry.firstName, entry.lastName) : '—'}
                </p>
                <p className="text-xs text-on-surface-variant">{entry.email}</p>
            </td>
            <td className="px-5 py-3 text-on-surface-variant">{roleLabel(entry.role)}</td>
            <td className="px-5 py-3">
                <Badge variant={entry.registered ? 'primary' : 'outline'}>
                    {entry.registered ? 'Registrado' : 'Sin registrar'}
                </Badge>
            </td>
            <td className="px-5 py-3 text-right">
                <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => onRemove(entry)}
                    disabled={removing}
                    aria-label={`Quitar del listado a ${entry.email}`}
                >
                    <X />
                </Button>
            </td>
        </tr>
    );
}

/**
 * El listado de la cátedra: quién la cursa o la dicta este semestre. Al
 * registrarse, cada persona del listado nace con el rol que dice aquí; quien
 * no está, como estudiante. Se carga desde un CSV y se puede corregir fila a
 * fila. Es una pantalla de la gestión.
 */
export function RosterPanel() {
    const { data: entries = [], isPending, error, refetch } = useRoster();
    const importRoster = useImportRoster();
    const removeEntry = useRemoveRosterEntry();
    const clearRoster = useClearRoster();
    const picker = useRef(null);
    const [result, setResult] = useState(null);
    const [search, setSearch] = useState('');
    const [confirmClear, setConfirmClear] = useState(false);
    const deferredSearch = useDeferredValue(search);

    const filtered = useMemo(() => {
        const term = normalizeText(deferredSearch.trim());
        return entries.filter(
            (entry) => !term || normalizeText(`${entry.firstName} ${entry.lastName} ${entry.email}`).includes(term),
        );
    }, [entries, deferredSearch]);
    const registered = entries.filter((entry) => entry.registered).length;

    const onPick = async (event) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        const problem = validateSize(file);
        if (problem) {
            toast.error(problem);
            return;
        }
        try {
            setResult(await importRoster.mutateAsync(file));
        } catch (importError) {
            toast.error(importError.body?.fields?.file ?? importError.message);
        }
    };

    const remove = (entry) =>
        removeEntry.mutate(entry.id, {
            onSuccess: () => toast.success(`${entry.email} salió del listado`),
            onError: (removeError) => toast.error(removeError.message),
        });

    const clear = () => {
        setConfirmClear(false);
        clearRoster.mutate(undefined, {
            onSuccess: () => toast.success('El listado quedó vacío'),
            onError: (clearError) => toast.error(clearError.message),
        });
    };

    return (
        <div className="flex flex-col gap-4">
            <input
                ref={picker}
                type="file"
                accept=".csv,text/csv,text/plain"
                onChange={onPick}
                className="sr-only"
                aria-label="Elegir el archivo del listado"
                tabIndex={-1}
            />
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="max-w-2xl text-sm text-on-surface-variant">
                    Quien esté aquí se registra con su rol; quien no, como estudiante. Un CSV con las columnas{' '}
                    <code className="label-mono">correo;rol</code> y, si quieres,{' '}
                    <code className="label-mono">nombres;apellidos</code>. El rol es «estudiante» o «profesor». Cargar otra vez
                    añade y actualiza, no borra.
                </p>
                <div className="flex shrink-0 flex-wrap gap-2">
                    <Button variant="outline" onClick={downloadTemplate}>
                        <Download /> Plantilla
                    </Button>
                    <Button onClick={() => picker.current?.click()} loading={importRoster.isPending}>
                        <FileUp /> Cargar listado
                    </Button>
                </div>
            </div>

            {result && <ImportSummary result={result} onClose={() => setResult(null)} />}

            {error ? (
                <ErrorState title="No pudimos cargar el listado" error={error} onRetry={refetch} />
            ) : isPending ? (
                <Skeleton className="h-40 rounded" aria-busy="true" />
            ) : entries.length === 0 ? (
                <EmptyState
                    icon={ListChecks}
                    title="Todavía no hay listado"
                    description="Carga el CSV de la cátedra para que cada persona se registre con su rol."
                />
            ) : (
                <>
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                        <SearchInput
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            placeholder="Buscar por nombre o correo"
                            label="Buscar en el listado"
                            className="flex-1"
                        />
                        <Badge variant="outline" mono className="self-start sm:self-center" aria-live="polite">
                            {filtered.length} de {entries.length} · {registered} con cuenta
                        </Badge>
                        <Button variant="ghost" size="sm" onClick={() => setConfirmClear(true)} disabled={clearRoster.isPending}>
                            <Trash2 /> Vaciar listado
                        </Button>
                    </div>
                    <ul className="flex flex-col gap-2 md:hidden" aria-label="Listado">
                        {filtered.map((entry) => (
                            <EntryCard key={entry.id} entry={entry} onRemove={remove} removing={removeEntry.isPending} />
                        ))}
                        {filtered.length === 0 && (
                            <li className="text-sm text-on-surface-variant">Nadie coincide con la búsqueda.</li>
                        )}
                    </ul>
                    <Card className="hidden overflow-hidden md:block">
                        <table className="w-full text-left text-sm">
                            <thead className="border-b border-outline-variant/60 bg-surface-container-low">
                                <tr className="label-mono text-on-surface-variant">
                                    <th scope="col" className="px-5 py-3">
                                        Persona
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        Rol
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        Cuenta
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        <span className="sr-only">Quitar</span>
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-outline-variant/50">
                                {filtered.map((entry) => (
                                    <Row key={entry.id} entry={entry} onRemove={remove} removing={removeEntry.isPending} />
                                ))}
                            </tbody>
                        </table>
                        {filtered.length === 0 && (
                            <p className="px-5 py-6 text-sm text-on-surface-variant">Nadie coincide con la búsqueda.</p>
                        )}
                    </Card>
                </>
            )}

            <ConfirmDialog
                open={confirmClear}
                title="¿Vaciar el listado?"
                description="Se quitan todas las filas. Las cuentas que ya se registraron no cambian."
                confirmLabel="Vaciar"
                onConfirm={clear}
                onClose={() => setConfirmClear(false)}
            />
        </div>
    );
}
