/**
 * El token de un enlace enviado por correo. Va en el fragmento de la dirección
 * (`#token=...`), que el navegador no envía al servidor ni queda en sus registros.
 *
 * @param {string} hash  `location.hash`, con o sin el `#` inicial
 * @returns {string | null}
 */
export const linkToken = (hash = '') => new URLSearchParams(hash.replace(/^#/, '')).get('token') || null;
