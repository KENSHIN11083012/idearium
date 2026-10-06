import { Controller } from 'react-hook-form';
import { Checkbox } from '@/components/ui/checkbox';

const NAME = 'dataConsent';

/**
 * Autorización de tratamiento de datos personales (Ley 1581 de 2012) para
 * formularios de react-hook-form con el campo `dataConsent`. La usan el registro
 * y el primer ingreso de las cuentas creadas desde la gestión.
 */
export function DataConsentField({ control }) {
    return (
        <Controller
            control={control}
            name={NAME}
            render={({ field, fieldState }) => (
                <div className="flex flex-col gap-1.5">
                    <div className="flex items-start gap-3 rounded-lg border border-outline-variant/70 bg-surface-container-low p-4">
                        <Checkbox
                            id={NAME}
                            checked={field.value}
                            onCheckedChange={(checked) => field.onChange(checked === true)}
                            onBlur={field.onBlur}
                            ref={field.ref}
                            aria-invalid={fieldState.error ? true : undefined}
                            aria-describedby={fieldState.error ? `${NAME}-error` : undefined}
                            className="mt-0.5"
                        />
                        <label htmlFor={NAME} className="text-sm leading-relaxed text-on-surface">
                            Autorizo a la Universidad Simón Bolívar el tratamiento de mis datos personales conforme a la Ley 1581
                            de 2012.
                        </label>
                    </div>
                    {fieldState.error && (
                        <p id={`${NAME}-error`} role="alert" className="text-xs font-medium text-error">
                            {fieldState.error.message}
                        </p>
                    )}
                </div>
            )}
        />
    );
}
