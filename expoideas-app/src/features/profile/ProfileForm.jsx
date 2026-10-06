import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { affiliationFromUser, affiliationToApi } from '@/lib/affiliation';
import { requiresAffiliation } from '@/lib/roles';
import { placeServerErrors } from '@/lib/validation';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/feedback';
import { AffiliationFields } from '@/features/catalogs/AffiliationFields';
import { useUpdateProfile } from './queries';
import { personalDataSchema, profileSchema } from './schemas';

const formValues = (profile, withAffiliation) => ({
    firstName: profile?.firstName ?? '',
    lastName: profile?.lastName ?? '',
    ...(withAffiliation ? affiliationFromUser(profile) : {}),
});

/**
 * Datos personales y, si el rol la lleva, adscripción académica. Se monta cuando
 * el perfil ya cargó: el rol decide el esquema del formulario desde el primer render.
 */
export function ProfileForm({ profile }) {
    const withAffiliation = requiresAffiliation(profile.role);
    const update = useUpdateProfile();

    const form = useForm({
        resolver: zodResolver(withAffiliation ? profileSchema : personalDataSchema),
        defaultValues: formValues(profile, withAffiliation),
    });
    const {
        register,
        handleSubmit,
        reset,
        setError,
        control,
        formState: { errors, isSubmitting, isDirty },
    } = form;

    const submit = async ({ firstName, lastName, ...affiliation }) => {
        const body = withAffiliation ? { firstName, lastName, ...affiliationToApi(affiliation) } : { firstName, lastName };
        try {
            const updated = await update.mutateAsync(body);
            reset(formValues(updated, withAffiliation));
            toast.success('Tus datos se actualizaron');
        } catch (error) {
            // Lo que no va junto a un campo se avisa: aquí no hay sitio para un error general.
            const general = placeServerErrors(error, setError, control);
            if (general) toast.error(general);
        }
    };

    return (
        <>
            {withAffiliation && profile.facultyId == null && (
                <Alert variant="info" title="Completa tu vínculo con la universidad">
                    Indica tu sede y facultad (y tu programa, si tienes uno) para que tu perfil quede completo.
                </Alert>
            )}

            <Card>
                <form onSubmit={handleSubmit(submit)} noValidate>
                    <CardHeader>
                        <CardTitle>Datos personales</CardTitle>
                        <CardDescription>Así aparece tu nombre en la plataforma.</CardDescription>
                    </CardHeader>
                    <CardContent className="grid gap-5 sm:grid-cols-2">
                        <Field label="Nombres" error={errors.firstName?.message} required>
                            <Input autoComplete="given-name" {...register('firstName')} />
                        </Field>
                        <Field label="Apellidos" error={errors.lastName?.message} required>
                            <Input autoComplete="family-name" {...register('lastName')} />
                        </Field>
                        <Field
                            label="Correo institucional"
                            hint="Es tu usuario de acceso; solo la administración puede cambiarlo."
                            className="sm:col-span-2"
                        >
                            <Input value={profile.email} readOnly disabled />
                        </Field>
                    </CardContent>
                    {withAffiliation && (
                        <>
                            <CardHeader className="border-t border-outline-variant/50">
                                <CardTitle as="h3">Adscripción académica</CardTitle>
                                <CardDescription>Tu sede, tu facultad y, si aplica, tu programa.</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <AffiliationFields form={{ ...form, errors }} />
                            </CardContent>
                        </>
                    )}
                    <CardFooter className="justify-end">
                        <Button type="button" variant="ghost" disabled={!isDirty || isSubmitting} onClick={() => reset()}>
                            Descartar
                        </Button>
                        <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
                            Guardar cambios
                        </Button>
                    </CardFooter>
                </form>
            </Card>
        </>
    );
}
