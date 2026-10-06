import { fileURLToPath, URL } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// El subdirectorio donde se sirve la app. App.jsx lo reutiliza como basename
// del router via import.meta.env.BASE_URL, para no mantenerlo en dos sitios.
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');

    return {
        base: env.VITE_BASE_PATH || '/expoideas/',
        plugins: [react(), tailwindcss()],
        resolve: {
            // import { Button } from '@/components/ui/button' en vez de rutas relativas.
            alias: {
                '@': fileURLToPath(new URL('./src', import.meta.url)),
            },
        },
        // Vitest usa esta misma configuración (alias @ incluido).
        test: {
            environment: 'jsdom',
            setupFiles: ['./src/test/setup.js'],
            css: false,
            // npm run test:coverage: cuánto del código de la app ejercitan las pruebas.
            // El informe navegable queda en coverage/index.html.
            coverage: {
                provider: 'v8',
                reporter: ['text-summary', 'json-summary', 'html'],
                include: ['src/**/*.{js,jsx}'],
                exclude: ['src/**/*.test.{js,jsx}', 'src/test/**', 'src/main.jsx'],
            },
        },
        build: {
            // Las fuentes siempre como archivo, nunca incrustadas como data:. Así las
            // permite la CSP de Nginx (font-src 'self') y el navegador las guarda en caché.
            assetsInlineLimit: (archivo) => (/\.(woff2?|ttf|otf)$/.test(archivo) ? false : undefined),
            rollupOptions: {
                output: {
                    // Dependencias en chunks propios: cambian menos que el código de la app,
                    // así el navegador las mantiene en caché entre despliegues.
                    manualChunks(id) {
                        if (!id.includes('node_modules')) return undefined;
                        if (/[\\/]node_modules[\\/](react|react-dom|scheduler|react-router|react-router-dom)[\\/]/.test(id))
                            return 'react';
                        if (/[\\/]node_modules[\\/](react-hook-form|zod|@hookform)[\\/]/.test(id)) return 'formularios';
                        return 'ui';
                    },
                },
            },
        },
    };
});
