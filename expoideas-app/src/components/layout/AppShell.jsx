import { Suspense, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Spinner } from '@/components/ui/feedback';
import { ErrorBoundary } from './ErrorBoundary';
import { SiteHeader } from './SiteHeader';
import { SiteFooter } from './SiteFooter';
import { PendingRoleNotice } from './PendingRoleNotice';

/** Al cambiar de página, vuelve arriba (el router no lo hace solo). */
function ScrollToTop() {
    const { pathname } = useLocation();
    useEffect(() => {
        window.scrollTo(0, 0);
    }, [pathname]);
    return null;
}

/**
 * Estructura de las páginas con navegación: enlace para saltar al contenido,
 * barra superior, contenido y pie de página.
 */
export function AppShell() {
    const { pathname } = useLocation();
    return (
        <div className="flex min-h-dvh flex-col">
            <a
                href="#main-content"
                className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-primary focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-on-primary"
            >
                Saltar al contenido
            </a>
            <ScrollToTop />
            <SiteHeader />
            <PendingRoleNotice />
            <main id="main-content" className="flex-1">
                {/* Si una página falla, el menú sigue ahí; al ir a otra página, el aviso se quita. */}
                <ErrorBoundary resetKey={pathname}>
                    <Suspense fallback={<Spinner className="min-h-[50vh]" />}>
                        <Outlet />
                    </Suspense>
                </ErrorBoundary>
            </main>
            <SiteFooter />
        </div>
    );
}

/** Contenedor estándar de las páginas internas. */
export function PageContainer({ children }) {
    return <div className="mx-auto flex w-full max-w-7xl flex-col gap-8 px-4 py-8 sm:px-6 lg:px-8 lg:py-12">{children}</div>;
}
