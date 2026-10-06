import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
// Fuentes empaquetadas con la app: no dependen de Google Fonts en tiempo de ejecución.
import '@fontsource-variable/inter';
import '@fontsource-variable/hanken-grotesk';
import '@fontsource-variable/jetbrains-mono';
import './index.css';
import App from '@/App';
import { ErrorBoundary } from '@/components/layout/ErrorBoundary';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { createQueryClient } from '@/lib/queryClient';

const queryClient = createQueryClient();

createRoot(document.getElementById('root')).render(
    <StrictMode>
        {/* El último recurso: si falla algo por encima de las páginas, que no quede la pantalla en blanco. */}
        <ErrorBoundary>
            <QueryClientProvider client={queryClient}>
                <AuthProvider>
                    <App />
                    <Toaster position="top-right" richColors closeButton toastOptions={{ className: 'font-sans' }} />
                </AuthProvider>
            </QueryClientProvider>
        </ErrorBoundary>
    </StrictMode>,
);
