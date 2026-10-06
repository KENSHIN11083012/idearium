import { useDeferredValue, useState } from 'react';
import { toast } from 'sonner';
import { RotateCw, UserPlus, UserX, Users } from 'lucide-react';
import { useAuth } from '@/features/auth/useAuth';
import { EMPTY_PAGE } from '@/lib/query';
import { ROLES, ROLE_LABELS, isManagement } from '@/lib/roles';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { NativeSelect } from '@/components/ui/native-select';
import { Pagination } from '@/components/ui/pagination';
import { SearchInput } from '@/components/ui/search-input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AffiliationDialog, NewUserDialog, PasswordResetDialog, TrackApprovalsDialog } from './UserDialogs';
import { RosterPanel } from './RosterPanel';
import { UserList } from './UserList';
import { useDeleteUser, useSetSuspended, useUpdateUser, useUsers } from './queries';

const PAGE_SIZE = 25;

/** En el filtro de rol: las cuentas que el listado de la cátedra dejó con un rol por confirmar. */
const PENDING_ROLE = 'PENDING_ROLE';

/** Lo que implica cada rol de gestión, para confirmarlo antes de otorgarlo. */
const MANAGEMENT_SCOPE = {
    [ROLES.MACONDOLAB]: 'Podrá gestionar las cuentas de estudiantes, profesores y jurados, y la clasificación de proyectos.',
    [ROLES.ADMIN]: 'Tendrá acceso completo a la plataforma, incluidas las cuentas de gestión y la estructura institucional.',
};

function LoadingUsers() {
    return (
        <div className="flex flex-col gap-3" aria-hidden="true">
            {[0, 1, 2, 3].map((index) => (
                <Skeleton key={index} className="h-16 rounded-lg" />
            ))}
        </div>
    );
}

export default function UserAdminPage() {
    const { user: me, role: actor } = useAuth();
    const updateUser = useUpdateUser();
    const deleteUser = useDeleteUser();
    const setSuspended = useSetSuspended();

    const [search, setSearch] = useState('');
    const [roleFilter, setRoleFilter] = useState('');
    const [dialog, setDialog] = useState(null); // { type: 'new' | 'affiliation' | 'password', user? }
    const [roleGrant, setRoleGrant] = useState(null); // { user, role }
    const [toDelete, setToDelete] = useState(null);
    const [toSuspend, setToSuspend] = useState(null);
    const [page, setPage] = useState(0);
    // La página se aplaza junto con la búsqueda: si no, se pediría la primera página del filtro anterior.
    const deferredSearch = useDeferredValue(search);
    const deferredPage = useDeferredValue(page);

    // La búsqueda, el filtro y las páginas los resuelve la API: aquí solo llega la página pedida.
    const {
        data: { items: users, totalItems, totalPages } = EMPTY_PAGE,
        isPending,
        isFetching,
        error,
        refetch,
    } = useUsers({
        search: deferredSearch.trim(),
        role: roleFilter === PENDING_ROLE ? '' : roleFilter,
        pendingRole: roleFilter === PENDING_ROLE,
        page: deferredPage,
        size: PAGE_SIZE,
    });

    // Si la página pedida ya no existe (se eliminó lo último que tenía), se vuelve a la última que hay.
    if (totalPages > 0 && page === deferredPage && page >= totalPages) {
        setPage(totalPages - 1);
    }

    // Un filtro nuevo empieza por la primera página.
    const filterBy = (setFilter) => (value) => {
        setFilter(value);
        setPage(0);
    };

    const isOwn = (user) => user.email?.toLowerCase() === me?.email?.toLowerCase();
    const isBusy = (user) => updateUser.isPending && updateUser.variables?.id === user.id;
    const hasFilters = Boolean(search || roleFilter);

    const changeRole = (user, role) =>
        updateUser.mutate(
            { id: user.id, changes: { role } },
            {
                onSuccess: () => toast.success(`Rol de ${user.firstName} actualizado`),
                onError: (changeError) => toast.error(changeError.message),
            },
        );

    /**
     * Resuelve el rol que el listado de la cátedra dejó pendiente: dárselo, o dejar
     * la cuenta con el que tiene. En los dos casos se envía un rol, que es lo que
     * le dice a la API que la gestión ya decidió.
     */
    const resolvePendingRole = (user, role) =>
        updateUser.mutate(
            { id: user.id, changes: { role } },
            {
                onSuccess: () =>
                    toast.success(
                        role === user.role
                            ? `${user.firstName} sigue como ${ROLE_LABELS[role]}`
                            : `${user.firstName} ya es ${ROLE_LABELS[role]}`,
                    ),
                onError: (resolveError) => toast.error(resolveError.message),
            },
        );

    // Otorgar un rol de gestión se confirma; los demás cambios se aplican directo.
    const onRoleChange = (user, role) => (isManagement(role) ? setRoleGrant({ user, role }) : changeRole(user, role));

    const changeSuspension = (user, suspended) =>
        setSuspended.mutate(
            { id: user.id, suspended },
            {
                onSuccess: () =>
                    toast.success(
                        suspended
                            ? `La cuenta de ${user.firstName} quedó suspendida`
                            : `${user.firstName} ya puede volver a entrar`,
                    ),
                onError: (suspensionError) => toast.error(suspensionError.message),
            },
        );

    // Suspender corta el acceso de inmediato: se confirma. Reactivar se aplica directo.
    const onSetSuspended = (user, suspended) => (suspended ? setToSuspend(user) : changeSuspension(user, false));

    // Un segundo clic alcanza al botón mientras el diálogo se cierra, cuando ya no hay a quién eliminar.
    const confirmDelete = () => {
        if (!toDelete) return;
        deleteUser.mutate(toDelete.id, {
            onSuccess: () => toast.success(`${toDelete.firstName} ${toDelete.lastName} fue eliminado`),
            onError: (deleteError) => toast.error(deleteError.message),
        });
    };

    const closeDialog = () => setDialog(null);

    return (
        <PageContainer>
            <PageHeader
                eyebrow="Gestión"
                title="Usuarios"
                description="Consulta las cuentas, asigna roles, crea cuentas para jurados, restablece contraseñas y suspende accesos."
                actions={
                    <>
                        <Button variant="outline" onClick={() => refetch()} disabled={isFetching}>
                            <RotateCw className={isFetching ? 'animate-spin' : undefined} /> Actualizar
                        </Button>
                        <Button onClick={() => setDialog({ type: 'new' })}>
                            <UserPlus /> Nueva cuenta
                        </Button>
                    </>
                }
            />

            <Tabs defaultValue="cuentas">
                <TabsList aria-label="Usuarios">
                    <TabsTrigger value="cuentas">Cuentas</TabsTrigger>
                    <TabsTrigger value="listado">Listado de la cátedra</TabsTrigger>
                </TabsList>
                <TabsContent value="listado" className="pt-6">
                    <RosterPanel />
                </TabsContent>
                <TabsContent value="cuentas" className="flex flex-col gap-6 pt-6">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                        <SearchInput
                            value={search}
                            onChange={(event) => filterBy(setSearch)(event.target.value)}
                            placeholder="Buscar por nombre o correo"
                            label="Buscar usuarios"
                            className="flex-1"
                        />
                        <NativeSelect
                            value={roleFilter}
                            onChange={(event) => filterBy(setRoleFilter)(event.target.value)}
                            aria-label="Filtrar por rol"
                            className="sm:w-52"
                        >
                            <option value="">Todos los roles</option>
                            {Object.values(ROLES).map((role) => (
                                <option key={role} value={role}>
                                    {ROLE_LABELS[role]}
                                </option>
                            ))}
                            <option value={PENDING_ROLE}>Con rol por confirmar</option>
                        </NativeSelect>
                        {!isPending && (
                            <Badge variant="outline" mono className="self-start sm:self-center" aria-live="polite">
                                {totalItems} {totalItems === 1 ? 'cuenta' : 'cuentas'}
                            </Badge>
                        )}
                    </div>

                    {error ? (
                        <ErrorState title="No pudimos cargar los usuarios" error={error} onRetry={refetch} />
                    ) : isPending ? (
                        <LoadingUsers />
                    ) : users.length === 0 ? (
                        <EmptyState
                            icon={hasFilters ? UserX : Users}
                            title={hasFilters ? 'Sin resultados' : 'Aún no hay usuarios'}
                            description={
                                hasFilters
                                    ? 'Prueba con otro nombre, correo o rol.'
                                    : 'Las cuentas aparecerán aquí cuando alguien se registre.'
                            }
                            action={
                                hasFilters && (
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => {
                                            setSearch('');
                                            setRoleFilter('');
                                            setPage(0);
                                        }}
                                    >
                                        Limpiar filtros
                                    </Button>
                                )
                            }
                        />
                    ) : (
                        <UserList
                            users={users}
                            actor={actor}
                            isOwn={isOwn}
                            isBusy={isBusy}
                            handlers={{
                                onRoleChange,
                                onEditAffiliation: (user) => setDialog({ type: 'affiliation', user }),
                                onTrackApprovals: (user) => setDialog({ type: 'approvals', user }),
                                onResetPassword: (user) => setDialog({ type: 'password', user }),
                                onSetSuspended,
                                onResolvePendingRole: resolvePendingRole,
                                onDelete: setToDelete,
                            }}
                        />
                    )}
                    {!error && (
                        <Pagination
                            page={page}
                            totalPages={totalPages}
                            totalItems={totalItems}
                            onChange={setPage}
                            label="Páginas de cuentas"
                        />
                    )}
                </TabsContent>
            </Tabs>

            {dialog?.type === 'new' && <NewUserDialog actor={actor} onClose={closeDialog} />}
            {dialog?.type === 'affiliation' && <AffiliationDialog user={dialog.user} onClose={closeDialog} />}
            {dialog?.type === 'password' && <PasswordResetDialog user={dialog.user} onClose={closeDialog} />}
            {dialog?.type === 'approvals' && <TrackApprovalsDialog user={dialog.user} onClose={closeDialog} />}

            <ConfirmDialog
                open={Boolean(roleGrant)}
                title={`¿Dar el rol ${ROLE_LABELS[roleGrant?.role]} a ${roleGrant?.user.firstName} ${roleGrant?.user.lastName}?`}
                description={MANAGEMENT_SCOPE[roleGrant?.role]}
                confirmLabel="Dar el rol"
                onConfirm={() => roleGrant && changeRole(roleGrant.user, roleGrant.role)}
                onClose={() => setRoleGrant(null)}
            />

            <ConfirmDialog
                open={Boolean(toSuspend)}
                title={`¿Suspender la cuenta de ${toSuspend?.firstName} ${toSuspend?.lastName}?`}
                description={`${toSuspend?.email} no podrá iniciar sesión y se cerrará la que tenga abierta. Sus proyectos, entregas y evaluaciones se conservan, y puedes reactivarla cuando quieras.`}
                confirmLabel="Suspender"
                onConfirm={() => toSuspend && changeSuspension(toSuspend, true)}
                onClose={() => setToSuspend(null)}
            />

            <ConfirmDialog
                open={Boolean(toDelete)}
                title={`¿Eliminar a ${toDelete?.firstName} ${toDelete?.lastName}?`}
                description={`Se borrará la cuenta ${toDelete?.email}. Esta acción no se puede deshacer.`}
                confirmLabel="Eliminar"
                onConfirm={confirmDelete}
                onClose={() => setToDelete(null)}
            />
        </PageContainer>
    );
}
