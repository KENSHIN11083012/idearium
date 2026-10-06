import { fileUrl } from '@/lib/files';
import { fullName } from '@/lib/text';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { AffiliationSummary, OnboardingBadge, PendingRoleBadge, RoleSelector, SuspendedBadge, UserActions } from './UserDetails';

/**
 * Cuentas en tabla (escritorio) y en tarjetas (celular).
 *
 * @param {object} props.handlers { onRoleChange, onEditAffiliation, onResetPassword, onSetSuspended,
 *                                  onResolvePendingRole, onDelete }
 * @param {(user) => boolean} props.isOwn   si es la cuenta de la sesión
 * @param {(user) => boolean} props.isBusy  si su rol se está guardando
 */
export function UserList({ users, actor, isOwn, isBusy, handlers }) {
    const { onRoleChange, ...actions } = handlers;
    const roleSelector = (user) => (
        <RoleSelector user={user} actor={actor} isOwn={isOwn(user)} disabled={isBusy(user)} onChange={onRoleChange} />
    );
    const userActions = (user) => <UserActions user={user} actor={actor} isOwn={isOwn(user)} {...actions} />;
    const avatar = (user) => <Avatar firstName={user.firstName} lastName={user.lastName} photoUrl={fileUrl(user.photoId)} />;

    return (
        <>
            {/* Escritorio: tabla */}
            <Card className="hidden overflow-hidden md:block">
                <table className="w-full text-left text-sm">
                    <thead className="border-b border-outline-variant/60 bg-surface-container-low">
                        <tr className="label-mono text-on-surface-variant">
                            <th scope="col" className="px-5 py-3">
                                Usuario
                            </th>
                            <th scope="col" className="px-5 py-3">
                                Adscripción
                            </th>
                            <th scope="col" className="px-5 py-3">
                                Rol
                            </th>
                            <th scope="col" className="px-5 py-3">
                                <span className="sr-only">Acciones</span>
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant/50">
                        {users.map((user) => (
                            <tr key={user.id} className="transition-colors hover:bg-surface-container-low/60">
                                <td className="px-5 py-3">
                                    <div className="flex items-center gap-3">
                                        {avatar(user)}
                                        <div className="min-w-0">
                                            <p className="font-semibold text-on-surface">
                                                {fullName(user.firstName, user.lastName)}
                                                {isOwn(user) && (
                                                    <Badge variant="lime" mono className="ml-2">
                                                        Tú
                                                    </Badge>
                                                )}
                                                <OnboardingBadge user={user} className="ml-2" />
                                                <SuspendedBadge user={user} className="ml-2" />
                                                <PendingRoleBadge user={user} className="ml-2" />
                                            </p>
                                            <p className="truncate text-on-surface-variant">{user.email}</p>
                                        </div>
                                    </div>
                                </td>
                                <td className="px-5 py-3 text-on-surface-variant">
                                    <AffiliationSummary user={user} />
                                </td>
                                <td className="px-5 py-3">{roleSelector(user)}</td>
                                <td className="px-5 py-3 text-right">{userActions(user)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </Card>

            {/* Celular: tarjetas */}
            <ul className="flex flex-col gap-3 md:hidden">
                {users.map((user) => (
                    <li key={user.id}>
                        <Card className="flex flex-col gap-4 p-4">
                            <div className="flex items-start gap-3">
                                {avatar(user)}
                                <div className="min-w-0 flex-1">
                                    <p className="font-semibold">{fullName(user.firstName, user.lastName)}</p>
                                    <OnboardingBadge user={user} className="mt-1" />
                                    <SuspendedBadge user={user} className="mt-1" />
                                    <PendingRoleBadge user={user} className="mt-1" />
                                    <p className="truncate text-sm text-on-surface-variant">{user.email}</p>
                                    <div className="mt-1 text-sm text-on-surface-variant">
                                        <AffiliationSummary user={user} />
                                    </div>
                                </div>
                                {userActions(user)}
                            </div>
                            {roleSelector(user)}
                        </Card>
                    </li>
                ))}
            </ul>
        </>
    );
}
