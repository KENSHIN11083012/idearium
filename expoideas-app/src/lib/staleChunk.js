/**
 * Cada página se descarga cuando se visita (App.jsx) y su archivo lleva un hash
 * en el nombre. Tras un despliegue, una pestaña que quedó abierta pide archivos
 * que ya no existen: la página no carga hasta recargar. Aquí se reconoce ese
 * fallo y se recarga una sola vez.
 */

const RELOADED_AT = 'expoideas:reloaded-at';

/** Margen para no recargar en bucle si recargar no lo arregla. */
const MIN_GAP_MS = 60_000;

/** Los mensajes de Chrome, Firefox y Safari, y el de Vite cuando falta la hoja de estilos. */
const STALE_CHUNK = /dynamically imported module|Importing a module script failed|Unable to preload CSS/i;

export const isStaleChunkError = (error) => STALE_CHUNK.test(error?.message ?? '');

/**
 * Recarga la página, salvo que ya se haya recargado hace un momento.
 *
 * @returns {boolean} si recargó
 */
export const reloadForNewVersion = (reload = () => window.location.reload()) => {
    try {
        const last = Number(sessionStorage.getItem(RELOADED_AT));
        if (last && Date.now() - last < MIN_GAP_MS) return false;
        sessionStorage.setItem(RELOADED_AT, String(Date.now()));
    } catch {
        // Sin sessionStorage no hay cómo saber si ya se recargó: mejor no arriesgar un bucle.
        return false;
    }
    reload();
    return true;
};
