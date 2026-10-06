import { useState } from 'react';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';

/**
 * Evita perder lo escrito en un diálogo por un Escape o un clic fuera: si hay
 * cambios sin guardar, cerrar pregunta antes.
 *
 * @param {boolean} dirty      si el formulario tiene cambios sin guardar
 * @param {() => void} onClose cómo se cierra de verdad
 * @returns {{ requestClose: () => void, discardDialog: JSX.Element }} `requestClose` va en todo lo
 *   que cierra (Cancelar, la X, Escape, clic fuera) y `discardDialog` se pinta junto al diálogo
 */
export function useDiscardGuard(dirty, onClose) {
    const [asking, setAsking] = useState(false);

    const requestClose = () => {
        if (dirty) setAsking(true);
        else onClose();
    };

    const discardDialog = (
        <ConfirmDialog
            open={asking}
            title="¿Descartar lo que escribiste?"
            description="Todavía no se ha guardado. Si cierras ahora, se pierde."
            confirmLabel="Descartar"
            cancelLabel="Seguir editando"
            onConfirm={() => {
                setAsking(false);
                onClose();
            }}
            onClose={() => setAsking(false)}
        />
    );

    return { requestClose, discardDialog };
}
