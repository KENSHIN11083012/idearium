import { useDeferredValue, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Download, FolderOpen, SearchX } from 'lucide-react';
import { toast } from 'sonner';
import { EMPTY_PAGE } from '@/lib/query';
import { ROLES } from '@/lib/roles';
import { ROUTES } from '@/lib/routes';
import { byName } from '@/lib/text';
import { TRACK_LIST, trackLabel } from '@/lib/tracks';
import { useAuth } from '@/features/auth/useAuth';
import { CATALOG_PATHS } from '@/features/catalogs/api';
import { useCatalogItems } from '@/features/catalogs/queries';
import { useEditions } from '@/features/editions/queries';
import { formatGrade, scaleLabel } from '@/features/evaluations/grades';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { Field } from '@/components/ui/field';
import { NativeSelect } from '@/components/ui/native-select';
import { Pagination } from '@/components/ui/pagination';
import { SearchInput } from '@/components/ui/search-input';
import { downloadProjectsCsv, useProjectDirectory } from './directory';
import { useTeachers } from './queries';
import { RESULTS, RESULT_LABELS, resultLabel } from './schemas';

const PAGE_SIZE = 25;

const NO_FILTERS = { editionId: '', track: '', teacherId: '', sectorId: '', result: '', search: '' };

/** Órdenes del listado. Espejo de ProjectSort en la API; sin valor, la API usa el primero. */
const SORTS = [
    { value: '', label: 'Más recientes primero' },
    { value: 'TITLE', label: 'Título (A–Z)' },
];

/** El resultado, si ya lo pusieron; si no, un guion para no llenar la tabla de texto. */
function ResultBadge({ project }) {
    if (!project.result) {
        return <span className="text-on-surface-variant">—</span>;
    }
    return <Badge variant={project.result === RESULTS.APPROVED ? 'primary' : 'outline'}>{resultLabel(project.result)}</Badge>;
}

/** Entregables: cuántos obligatorios lleva el proyecto. */
function DeliverablesBadge({ project }) {
    if (project.requiredDeliverables === 0) {
        return <Badge variant="outline">Sin entregables configurados</Badge>;
    }
    const complete = project.deliveredDeliverables >= project.requiredDeliverables;
    return (
        <Badge variant={complete ? 'primary' : 'outline'}>
            {complete ? 'Entregado' : `Faltan ${project.requiredDeliverables - project.deliveredDeliverables}`}
        </Badge>
    );
}

/** La nota del proyecto (promedio de los jurados que ya calificaron) y cuántos van. */
function GradeCell({ project }) {
    if (project.grade == null) {
        return (
            <span className="text-on-surface-variant" title="Ningún jurado ha calificado">
                —
            </span>
        );
    }
    return (
        <span className="flex flex-col">
            <span>
                <strong className="text-on-surface">{formatGrade(project.grade)}</strong>
                <span className="text-on-surface-variant"> · {scaleLabel(project.scale)}</span>
            </span>
            <span className="text-xs text-on-surface-variant">
                {project.evaluated} de {project.jurors} {project.jurors === 1 ? 'jurado' : 'jurados'}
            </span>
        </span>
    );
}

/** Integrantes aceptados, resaltando al grupo que no llega al mínimo. */
function TeamCount({ project }) {
    const short = project.members < project.minMembers;
    return (
        <p className={short ? 'text-xs font-medium text-tertiary' : 'text-xs text-on-surface-variant'}>
            {project.members} {project.members === 1 ? 'integrante' : 'integrantes'}
            {short ? ` · mínimo ${project.minMembers}` : ''}
        </p>
    );
}

function ProjectRow({ project }) {
    return (
        <tr className="transition-colors hover:bg-surface-container-low/60">
            <td className="px-5 py-3">
                <Link to={ROUTES.project(project.id)} className="font-medium text-primary hover:underline">
                    {project.title}
                </Link>
                <p className="label-mono text-xs text-on-surface-variant">
                    {project.edition} · {trackLabel(project.track)}
                </p>
            </td>
            <td className="hidden px-5 py-3 text-on-surface-variant lg:table-cell">
                {project.sector}
                {project.prototypeType && <p className="text-xs">Prototipo: {project.prototypeType}</p>}
            </td>
            <td className="hidden px-5 py-3 text-on-surface-variant md:table-cell">{project.teacher}</td>
            <td className="px-5 py-3 text-on-surface-variant">
                {project.leader}
                <TeamCount project={project} />
            </td>
            <td className="px-5 py-3">
                <DeliverablesBadge project={project} />
            </td>
            <td className="px-5 py-3">
                <GradeCell project={project} />
            </td>
            <td className="px-5 py-3">
                <ResultBadge project={project} />
            </td>
            <td className="px-5 py-3 text-right">
                <Button variant="outline" size="sm" asChild>
                    <Link to={ROUTES.project(project.id)}>
                        Ver proyecto <ArrowRight />
                    </Link>
                </Button>
            </td>
        </tr>
    );
}

/**
 * El mismo proyecto en móvil: la tabla ahí deja el título en menos de 140 px y
 * lo parte en seis líneas, así que por debajo de md se muestra como tarjeta.
 */
function ProjectCard({ project }) {
    return (
        <li>
            <Card className="flex flex-col gap-3 p-5">
                <div className="flex flex-col gap-1">
                    <p className="label-mono text-on-surface-variant">
                        {project.edition} · {trackLabel(project.track)}
                    </p>
                    <Link to={ROUTES.project(project.id)} className="font-semibold text-primary hover:underline">
                        {project.title}
                    </Link>
                </div>
                <dl className="flex flex-col gap-1 text-sm">
                    <div className="flex gap-2">
                        <dt className="text-on-surface-variant">Sector:</dt>
                        <dd className="text-on-surface">{project.sector}</dd>
                    </div>
                    {project.prototypeType && (
                        <div className="flex gap-2">
                            <dt className="text-on-surface-variant">Prototipo:</dt>
                            <dd className="text-on-surface">{project.prototypeType}</dd>
                        </div>
                    )}
                    <div className="flex gap-2">
                        <dt className="text-on-surface-variant">Profesor:</dt>
                        <dd className="text-on-surface">{project.teacher}</dd>
                    </div>
                    <div className="flex gap-2">
                        <dt className="text-on-surface-variant">Líder:</dt>
                        <dd className="text-on-surface">{project.leader}</dd>
                    </div>
                    <div className="flex gap-2">
                        <dt className="text-on-surface-variant">Nota:</dt>
                        <dd>
                            <GradeCell project={project} />
                        </dd>
                    </div>
                </dl>
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-outline-variant/50 pt-3">
                    <TeamCount project={project} />
                    <div className="flex flex-wrap gap-2">
                        <DeliverablesBadge project={project} />
                        {project.result && <ResultBadge project={project} />}
                    </div>
                </div>
                <Button variant="outline" size="sm" asChild className="self-end">
                    <Link to={ROUTES.project(project.id)}>
                        Ver proyecto <ArrowRight />
                    </Link>
                </Button>
            </Card>
        </li>
    );
}

/**
 * Proyectos inscritos, para MacondoLab y para los profesores. La gestión los ve
 * todos y filtra; un profesor ve los que lo nombraron como profesor del grupo.
 */
export default function ProjectsPage() {
    const { role, isManagement } = useAuth();
    const [filters, setFilters] = useState(NO_FILTERS);
    // El orden viaja con los filtros (a la lista y al CSV), pero no es uno: «Quitar filtros» lo deja.
    const [sort, setSort] = useState('');
    const [page, setPage] = useState(0);
    const query = useMemo(() => ({ ...filters, sort }), [filters, sort]);
    // La página se aplaza junto con los filtros: si no, se pediría la primera página del filtro anterior.
    const deferred = useDeferredValue(query);
    const deferredPage = useDeferredValue(page);
    const {
        data: { items: projects, totalItems, totalPages } = EMPTY_PAGE,
        isPending,
        error,
        refetch,
    } = useProjectDirectory(deferred, { page: deferredPage, size: PAGE_SIZE });

    // Si la página pedida ya no existe (se eliminó lo último que tenía), se vuelve a la última que hay.
    if (totalPages > 0 && page === deferredPage && page >= totalPages) {
        setPage(totalPages - 1);
    }
    const { data: editions = [] } = useEditions();
    const { data: sectors = [] } = useCatalogItems(CATALOG_PATHS.sectors);
    const { data: teachers = [] } = useTeachers();
    const [downloading, setDownloading] = useState(false);

    // Un filtro nuevo empieza por la primera página.
    const set = (key) => (event) => {
        setFilters((current) => ({ ...current, [key]: event.target.value }));
        setPage(0);
    };
    const clear = () => {
        setFilters(NO_FILTERS);
        setPage(0);
    };
    const changeSort = (event) => {
        setSort(event.target.value);
        setPage(0);
    };
    const filtered = Object.values(filters).some(Boolean);

    const download = async () => {
        setDownloading(true);
        try {
            await downloadProjectsCsv(query);
        } catch (downloadError) {
            toast.error(downloadError.message);
        } finally {
            setDownloading(false);
        }
    };

    return (
        <PageContainer>
            <PageHeader
                eyebrow={isManagement ? 'Gestión' : 'Docencia'}
                title="Proyectos"
                description={
                    isManagement
                        ? 'Todos los proyectos inscritos, con su equipo y sus entregables. Filtra y descarga la lista para trabajarla aparte.'
                        : 'Los proyectos que te nombraron como profesor del grupo, con su equipo y sus entregables.'
                }
                actions={
                    <Button variant="outline" onClick={download} loading={downloading} disabled={projects.length === 0}>
                        <Download /> Descargar CSV
                    </Button>
                }
            />

            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <SearchInput
                    value={filters.search}
                    onChange={set('search')}
                    placeholder="Buscar por título"
                    label="Buscar proyectos"
                    className="sm:col-span-2"
                />
                <Field label="Edición">
                    <NativeSelect value={filters.editionId} onChange={set('editionId')}>
                        <option value="">Todas</option>
                        {editions.map((edition) => (
                            <option key={edition.id} value={String(edition.id)}>
                                {edition.name}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
                <Field label="Cátedra">
                    <NativeSelect value={filters.track} onChange={set('track')}>
                        <option value="">Las dos</option>
                        {TRACK_LIST.map((track) => (
                            <option key={track} value={track}>
                                {trackLabel(track)}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
                <Field label="Sector">
                    <NativeSelect value={filters.sectorId} onChange={set('sectorId')}>
                        <option value="">Todos</option>
                        {[...sectors].sort(byName).map((sector) => (
                            <option key={sector.id} value={String(sector.id)}>
                                {sector.name}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
                <Field label="Resultado">
                    <NativeSelect value={filters.result} onChange={set('result')}>
                        <option value="">Todos</option>
                        {Object.values(RESULTS).map((result) => (
                            <option key={result} value={result}>
                                {RESULT_LABELS[result]}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
                <Field label="Ordenar por">
                    <NativeSelect value={sort} onChange={changeSort}>
                        {SORTS.map(({ value, label }) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                    </NativeSelect>
                </Field>
                {role !== ROLES.TEACHER && (
                    <Field label="Profesor">
                        <NativeSelect value={filters.teacherId} onChange={set('teacherId')}>
                            <option value="">Todos</option>
                            {teachers.map((teacher) => (
                                <option key={teacher.id} value={String(teacher.id)}>
                                    {teacher.fullName}
                                </option>
                            ))}
                        </NativeSelect>
                    </Field>
                )}
            </div>

            {error ? (
                <ErrorState title="No pudimos cargar los proyectos" error={error} onRetry={refetch} />
            ) : isPending ? (
                <div className="flex flex-col gap-2" aria-hidden="true">
                    {[0, 1, 2, 3].map((index) => (
                        <Skeleton key={index} className="h-14 rounded" />
                    ))}
                </div>
            ) : projects.length === 0 ? (
                <EmptyState
                    icon={filtered ? SearchX : FolderOpen}
                    title={filtered ? 'Sin resultados' : 'Todavía no hay proyectos inscritos'}
                    description={
                        filtered ? 'Prueba con otros filtros.' : 'Cuando los equipos inscriban sus proyectos, aparecerán aquí.'
                    }
                    action={
                        filtered && (
                            <Button size="sm" variant="outline" onClick={clear}>
                                Quitar filtros
                            </Button>
                        )
                    }
                />
            ) : (
                <>
                    <ul className="flex flex-col gap-3 md:hidden" aria-label="Proyectos">
                        {projects.map((project) => (
                            <ProjectCard key={project.id} project={project} />
                        ))}
                    </ul>

                    <Card className="hidden overflow-hidden md:block">
                        <table className="w-full text-left text-sm">
                            <thead className="border-b border-outline-variant/60 bg-surface-container-low">
                                <tr className="label-mono text-on-surface-variant">
                                    <th scope="col" className="px-5 py-3">
                                        Proyecto
                                    </th>
                                    <th scope="col" className="hidden px-5 py-3 lg:table-cell">
                                        Sector
                                    </th>
                                    <th scope="col" className="hidden px-5 py-3 md:table-cell">
                                        Profesor
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        Equipo
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        Entregables
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        Nota
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        Resultado
                                    </th>
                                    <th scope="col" className="px-5 py-3">
                                        <span className="sr-only">Abrir</span>
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-outline-variant/50">
                                {projects.map((project) => (
                                    <ProjectRow key={project.id} project={project} />
                                ))}
                            </tbody>
                        </table>
                    </Card>

                    <p className="label-mono text-on-surface-variant" aria-live="polite">
                        {totalItems} {totalItems === 1 ? 'proyecto' : 'proyectos'}
                    </p>
                    <Pagination
                        page={page}
                        totalPages={totalPages}
                        totalItems={totalItems}
                        onChange={setPage}
                        label="Páginas de proyectos"
                    />
                </>
            )}
        </PageContainer>
    );
}
