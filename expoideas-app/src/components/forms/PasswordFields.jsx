import { useWatch } from 'react-hook-form';
import { Field } from '@/components/ui/field';
import { PasswordInput } from '@/components/ui/password-input';
import { PasswordRequirements } from './PasswordRequirements';

/**
 * Contraseña nueva con sus requisitos y la confirmación, para formularios de
 * react-hook-form con el campo `name` y `confirmPassword` (ver newPasswordShape
 * en lib/validation.js).
 *
 * @param {object} form    { register, control, errors } del useForm
 * @param {string} [name]  campo de la contraseña; el registro usa "password", como la API
 * @param {string} [label] etiqueta de la contraseña nueva
 */
export function PasswordFields({ form: { register, control, errors }, name = 'newPassword', label = 'Nueva contraseña' }) {
    const value = useWatch({ control, name });

    return (
        <>
            <div className="flex flex-col gap-2">
                <Field label={label} error={errors[name]?.message} required>
                    <PasswordInput autoComplete="new-password" {...register(name)} />
                </Field>
                <PasswordRequirements value={value} />
            </div>
            <Field label={`Confirmar ${label.toLowerCase()}`} error={errors.confirmPassword?.message} required>
                <PasswordInput autoComplete="new-password" {...register('confirmPassword')} />
            </Field>
        </>
    );
}
