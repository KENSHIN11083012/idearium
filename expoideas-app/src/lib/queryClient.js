import { QueryClient } from '@tanstack/react-query';

/**
 * Caché de los datos de la API (TanStack Query). Los catálogos y listados se
 * piden una vez y se comparten entre pantallas; cada mutación invalida lo que cambia.
 */
export const createQueryClient = () =>
    new QueryClient({
        defaultOptions: {
            queries: {
                // Un error 4xx no se arregla reintentando: solo se reintentan los de red y 5xx.
                retry: (failureCount, error) => failureCount < 2 && !(error?.status >= 400 && error?.status < 500),
                staleTime: 30_000,
                refetchOnWindowFocus: false,
            },
        },
    });
