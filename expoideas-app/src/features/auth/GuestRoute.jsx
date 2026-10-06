import { Navigate, useLocation } from 'react-router-dom';
import { startRouteFor } from '@/lib/routes';
import { useAuth } from './useAuth';

/**
 * Inicio de sesión y registro no tienen sentido con la sesión iniciada. Quien
 * llegó aquí porque una página pedía sesión (ProtectedRoute deja en `from` cuál)
 * vuelve a ella al entrar; los demás van al inicio de su rol.
 */
export function GuestRoute({ children }) {
    const { token, role, pendingSteps } = useAuth();
    const from = useLocation().state?.from;

    if (!token) return children;
    if (pendingSteps.length === 0 && from?.pathname) {
        return <Navigate to={{ pathname: from.pathname, search: from.search }} replace />;
    }
    return <Navigate to={startRouteFor(role, pendingSteps)} replace />;
}
