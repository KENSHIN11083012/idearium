import { Alert } from '@/components/ui/feedback';
import { Button } from '@/components/ui/button';
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog';
import { useDiscardGuard } from './useDiscardGuard';

/**
 * Diálogo con un formulario: título, descripción, error general, campos y pie
 * con Cancelar y el botón de envío. Se monta abierto y se cierra con onClose.
 *
 * Con cambios sin guardar (`dirty`), cerrarlo con Cancelar, la X, Escape o un
 * clic fuera pregunta antes de descartarlos.
 *
 * @param {(event) => void} onSubmit  normalmente handleSubmit(...) de react-hook-form
 * @param {string} [error]            error general (errors.root?.message)
 * @param {boolean} [dirty]           formState.isDirty del formulario
 */
export function FormDialog({
    title,
    description,
    onClose,
    onSubmit,
    error,
    submitLabel,
    submitting = false,
    submitDisabled = false,
    dirty = false,
    className,
    children,
}) {
    // Mientras se envía no se pregunta: lo escrito ya va camino de guardarse.
    const { requestClose, discardDialog } = useDiscardGuard(dirty && !submitting, onClose);

    return (
        <Dialog open onOpenChange={(open) => !open && requestClose()}>
            <DialogContent className={className}>
                {/* El formulario hereda la columna flexible del diálogo: así el cuerpo
                    es lo único que hace scroll y el pie con el botón de envío no se
                    sale de la pantalla. */}
                <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
                    <DialogHeader>
                        <DialogTitle>{title}</DialogTitle>
                        <DialogDescription>{description}</DialogDescription>
                    </DialogHeader>
                    <DialogBody>
                        {error && <Alert variant="error" title={error} />}
                        {children}
                    </DialogBody>
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={requestClose}>
                            Cancelar
                        </Button>
                        <Button type="submit" loading={submitting} disabled={submitDisabled}>
                            {submitLabel}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
            {discardDialog}
        </Dialog>
    );
}
