import { Navigate, useLocation } from 'react-router-dom';
import { hasRole } from '@/lib/roles';
import { ROUTES } from '@/lib/routes';
import { useAuth } from './useAuth';

/**
 * Guarda una ruta según la sesión y los roles permitidos:
 *
 * - Sin sesión -> inicio de sesión (y de vuelta aquí al entrar)
 * - Primer ingreso pendiente -> primer ingreso
 * - Sin permiso -> "no autorizado" (pero no antes de saber el rol actual:
 *   el del token puede estar viejo si la gestión lo cambió hace poco)
 *
 * @param {string[]} [allowedRoles] si se omite, basta con tener sesión.
 */
export function ProtectedRoute({ allowedRoles, children }) {
    const { token, role, roleReady, pendingSteps } = useAuth();
    const location = useLocation();

    if (!token) {
        return <Navigate to={ROUTES.LOGIN} state={{ from: location }} replace />;
    }
    if (pendingSteps.length > 0) {
        return <Navigate to={ROUTES.ONBOARDING} replace />;
    }
    if (!hasRole(role, allowedRoles)) {
        return roleReady ? <Navigate to={ROUTES.UNAUTHORIZED} replace /> : null;
    }
    return children;
}
