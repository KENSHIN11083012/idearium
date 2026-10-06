import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Al cambiar de página, el foco va al contenido principal. Sin esto se queda en
 * el enlace que se pulsó: quien navega con teclado o con lector de pantalla no
 * se entera de que la página cambió y tiene que recorrer el menú otra vez.
 *
 * Al cargar la app no se toca el foco: solo cuando la ruta cambia.
 */
export function RouteFocus() {
    const { pathname } = useLocation();
    const previous = useRef(pathname);

    useEffect(() => {
        if (previous.current === pathname) return;
        previous.current = pathname;
        const main = document.querySelector('main');
        if (!main) return;
        // tabindex -1: se le puede dar el foco por código sin meterlo en el orden del tabulador.
        main.setAttribute('tabindex', '-1');
        main.focus({ preventScroll: true });
    }, [pathname]);

    return null;
}
