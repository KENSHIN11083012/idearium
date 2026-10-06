/**
 * La cadena de consulta de una petición: «?search=ana&page=1», o '' si no hay
 * nada que enviar. Los valores vacíos no se envían: la API los trata como «sin
 * filtrar».
 *
 * @param {Record<string, unknown>} params
 */
export const toQuery = (params = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== '' && value !== null && value !== undefined && value !== false) query.set(key, value);
    });
    const text = query.toString();
    return text ? `?${text}` : '';
};

/** Una página vacía, con la forma de PageResponse: lo que se muestra mientras llega la primera. */
export const EMPTY_PAGE = { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 };
