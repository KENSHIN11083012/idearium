import { roleLabel } from '@/lib/roles';
import { useAuth } from '@/features/auth/useAuth';
import { Alert } from '@/components/ui/feedback';

/**
 * Aviso para quien figura en el listado de la cátedra con un rol que la gestión
 * todavía no confirma: mientras tanto su cuenta es de estudiante, y sin esto no
 * sabría por qué no ve lo que esperaba.
 */
export function PendingRoleNotice() {
    const { pendingRole } = useAuth();
    if (!pendingRole) return null;

    return (
        <div className="border-b border-outline-variant/60 bg-surface-container-low">
            <div className="mx-auto w-full max-w-7xl px-4 py-3 sm:px-6 lg:px-8">
                <Alert title={`Tu rol de ${roleLabel(pendingRole).toLowerCase()} está por confirmar`}>
                    Figuras así en el listado de la cátedra. Mientras la coordinación lo confirma, tu cuenta funciona como la de
                    un estudiante; cuando lo haga, el cambio aparece solo, sin volver a entrar.
                </Alert>
            </div>
        </div>
    );
}
