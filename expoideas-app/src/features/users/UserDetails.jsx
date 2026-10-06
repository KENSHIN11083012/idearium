import { Award, Ban, BadgeCheck, Ellipsis, GraduationCap, KeyRound, Trash2, UserCheck, X } from 'lucide-react';
import { ROLES, ROLE_LABELS, assignableRoles, canManage, isManagement, requiresAffiliation, roleLabel } from '@/lib/roles';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { NativeSelect } from '@/components/ui/native-select';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

/**
 * Rol de una cuenta. La propia cuenta y las que el rol de la sesión no gestiona
 * (MacondoLab frente a cuentas de gestión) solo lo muestran. La API aplica las
 * mismas reglas.
 */
export function RoleSelector({ user, actor, isOwn, disabled, onChange }) {
    if (isOwn || !canManage(actor, user.role)) {
        return (
            <Badge variant={isManagement(user.role) ? 'dark' : 'outline'} mono>
                {roleLabel(user.role)}
            </Badge>
        );
    }

    return (
        <NativeSelect
            aria-label={`Rol de ${user.firstName} ${user.lastName}`}
            value={user.role}
            disabled={disabled}
            onChange={(event) => onChange(user, event.target.value)}
            className="h-9 min-w-44 text-sm"
        >
            {assignableRoles(actor).map((role) => (
                <option key={role} value={role}>
                    {ROLE_LABELS[role]}
                </option>
            ))}
        </NativeSelect>
    );
}

/** La persona aún no cambia su contraseña temporal o no ha autorizado sus datos. */
export function OnboardingBadge({ user, className }) {
    if (!user.pendingSteps?.length) return null;
    return (
        <Badge variant="outline" mono className={className} title="Aún no completa su primer ingreso">
            Primer ingreso pendiente
        </Badge>
    );
}

/** Figura en el listado de la cátedra con un rol que la gestión aún no confirma. */
export function PendingRoleBadge({ user, className }) {
    if (!user.pendingRole) return null;
    return (
        <Badge variant="lime" mono className={className} title="Así figura en el listado de la cátedra">
            Pide rol: {roleLabel(user.pendingRole)}
        </Badge>
    );
}

/** La gestión le cortó el acceso: no inicia sesión hasta que la reactiven. */
export function SuspendedBadge({ user, className }) {
    if (!user.suspended) return null;
    return (
        <Badge variant="dark" mono className={className}>
            Suspendida
        </Badge>
    );
}

/** Facultad arriba; sede y programa debajo. La gestión y los jurados no tienen adscripción. */
export function AffiliationSummary({ user }) {
    if (!requiresAffiliation(user.role)) {
        return <p className="text-xs text-outline">No aplica</p>;
    }
    return (
        <>
            <p className={user.faculty ? undefined : 'italic text-outline'}>{user.faculty ?? 'Sin facultad'}</p>
            <p className="text-xs text-outline">
                {user.campus ?? 'Sin sede'} · {user.academicProgram ?? 'Sin programa'}
            </p>
        </>
    );
}

/** Menú de acciones sobre una cuenta que el rol de la sesión puede gestionar. */
export function UserActions({
    user,
    actor,
    isOwn,
    onEditAffiliation,
    onTrackApprovals,
    onResetPassword,
    onSetSuspended,
    onResolvePendingRole,
    onDelete,
}) {
    if (!canManage(actor, user.role)) return null;

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-sm" aria-label={`Acciones para ${user.firstName} ${user.lastName}`}>
                    <Ellipsis />
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
                {user.pendingRole && canManage(actor, user.pendingRole) && (
                    <>
                        <DropdownMenuItem onSelect={() => onResolvePendingRole(user, user.pendingRole)}>
                            <BadgeCheck /> Dar el rol de {roleLabel(user.pendingRole)}
                        </DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => onResolvePendingRole(user, user.role)}>
                            <X /> Descartar el rol del listado
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                    </>
                )}
                {requiresAffiliation(user.role) && (
                    <DropdownMenuItem onSelect={() => onEditAffiliation(user)}>
                        <GraduationCap /> Editar adscripción
                    </DropdownMenuItem>
                )}
                {user.role === ROLES.STUDENT && (
                    <DropdownMenuItem onSelect={() => onTrackApprovals(user)}>
                        <Award /> Cátedras aprobadas
                    </DropdownMenuItem>
                )}
                <DropdownMenuItem onSelect={() => onResetPassword(user)} disabled={isOwn}>
                    <KeyRound /> Restablecer contraseña
                </DropdownMenuItem>
                {user.suspended ? (
                    <DropdownMenuItem onSelect={() => onSetSuspended(user, false)}>
                        <UserCheck /> Reactivar cuenta
                    </DropdownMenuItem>
                ) : (
                    <DropdownMenuItem onSelect={() => onSetSuspended(user, true)} disabled={isOwn}>
                        <Ban /> Suspender cuenta
                    </DropdownMenuItem>
                )}
                {actor === ROLES.ADMIN && (
                    <>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                            variant="destructive"
                            onSelect={() => onDelete(user)}
                            disabled={user.role === ROLES.ADMIN || isOwn}
                        >
                            <Trash2 /> Eliminar usuario
                        </DropdownMenuItem>
                    </>
                )}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
