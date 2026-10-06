import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useForm } from 'react-hook-form';
import { FormDialog } from './FormDialog';

/** Un diálogo con un campo, como cualquiera de los de la app. */
function Sample({ onClose, submitting = false }) {
    const {
        register,
        handleSubmit,
        formState: { isDirty },
    } = useForm({ defaultValues: { name: 'Salud' } });
    return (
        <FormDialog
            title="Editar sector"
            description="Cambia el nombre."
            onClose={onClose}
            onSubmit={handleSubmit(() => {})}
            submitLabel="Guardar"
            submitting={submitting}
            dirty={isDirty}
        >
            <label>
                Nombre
                <input {...register('name')} />
            </label>
        </FormDialog>
    );
}

const renderSample = (props) => {
    const onClose = vi.fn();
    render(<Sample onClose={onClose} {...props} />);
    return onClose;
};

const typeSomething = () => userEvent.type(screen.getByLabelText('Nombre'), ' pública');
const question = () => within(screen.getByRole('alertdialog'));

describe('Cerrar un diálogo con un formulario', () => {
    it('sin cambios, se cierra sin preguntar', async () => {
        const onClose = renderSample();

        await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

        expect(onClose).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    });

    it('con algo escrito, Cancelar pregunta antes de descartarlo', async () => {
        const onClose = renderSample();
        await typeSomething();

        await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

        expect(question().getByText('¿Descartar lo que escribiste?')).toBeInTheDocument();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('«Seguir editando» deja el formulario como estaba', async () => {
        const onClose = renderSample();
        await typeSomething();
        await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

        await userEvent.click(question().getByRole('button', { name: 'Seguir editando' }));

        expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
        expect(screen.getByLabelText('Nombre')).toHaveValue('Salud pública');
        expect(onClose).not.toHaveBeenCalled();
    });

    it('«Descartar» lo cierra', async () => {
        const onClose = renderSample();
        await typeSomething();
        await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

        await userEvent.click(question().getByRole('button', { name: 'Descartar' }));

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('Escape y la X también preguntan: son las formas de cerrarlo sin querer', async () => {
        const onClose = renderSample();
        await typeSomething();

        await userEvent.keyboard('{Escape}');
        expect(question().getByText('¿Descartar lo que escribiste?')).toBeInTheDocument();
        await userEvent.click(question().getByRole('button', { name: 'Seguir editando' }));

        await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));
        expect(question().getByText('¿Descartar lo que escribiste?')).toBeInTheDocument();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('si lo escrito vuelve a ser lo que había, ya no hay nada que descartar', async () => {
        const onClose = renderSample();
        await typeSomething();
        await userEvent.clear(screen.getByLabelText('Nombre'));
        await userEvent.type(screen.getByLabelText('Nombre'), 'Salud');

        await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
