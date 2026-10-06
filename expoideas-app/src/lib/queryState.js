/**
 * El error de una consulta que debe reemplazar la página, o null.
 *
 * Con datos ya en pantalla, un fallo pasajero al volver a consultarlos (se fue la
 * conexión, la API tardó) no los quita: quien está llenando un formulario lo
 * perdería. Sí los quita un 4xx, porque es la API diciendo que eso ya no se
 * puede ver (dejó de ser jurado, el proyecto se eliminó).
 *
 * @param {{ data: unknown, error: (Error & { status?: number }) | null }} query
 */
export const blockingError = ({ data, error }) => {
    if (!error) return null;
    const refused = error.status >= 400 && error.status < 500;
    return data === undefined || refused ? error : null;
};
