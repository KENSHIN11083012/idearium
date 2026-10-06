import { Link } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import { useAuth } from '@/features/auth/useAuth';
import { blockingError } from '@/lib/queryState';
import { requiresAffiliation } from '@/lib/roles';
import { ROUTES } from '@/lib/routes';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ErrorState, Skeleton } from '@/components/ui/feedback';
import { IdentityCard } from './IdentityCard';
import { ProfileForm } from './ProfileForm';
import { useProfile } from './queries';

function LoadingProfile() {
    return (
        <div className="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]" aria-hidden="true">
            <Skeleton className="h-96 rounded-lg" />
            <div className="flex flex-col gap-6">
                <Skeleton className="h-64 rounded-lg" />
                <Skeleton className="h-32 rounded-lg" />
            </div>
        </div>
    );
}

function PasswordCard() {
    return (
        <Card>
            <CardHeader className="gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                        <KeyRound className="size-5" aria-hidden="true" />
                    </span>
                    <div className="flex flex-col gap-1">
                        <CardTitle>Contraseña</CardTitle>
                        <CardDescription>Cámbiala periódicamente y no la compartas.</CardDescription>
                    </div>
                </div>
                <Button asChild variant="outline">
                    <Link to={ROUTES.SECURITY}>Cambiar contraseña</Link>
                </Button>
            </CardHeader>
        </Card>
    );
}

export default function ProfilePage() {
    const { role } = useAuth();
    const { data: profile, error: lastError, refetch } = useProfile();
    // El perfil se vuelve a consultar cada minuto: un fallo pasajero no quita el formulario a medio llenar.
    const error = blockingError({ data: profile, error: lastError });

    return (
        <PageContainer>
            <PageHeader
                eyebrow="Mi cuenta"
                title="Mi perfil"
                description={
                    requiresAffiliation(role)
                        ? 'Mantén al día tus datos y tu vínculo con la universidad.'
                        : 'Mantén al día tus datos personales.'
                }
            />

            {error ? (
                <ErrorState title="No pudimos cargar tu perfil" error={error} onRetry={refetch} />
            ) : !profile ? (
                <LoadingProfile />
            ) : (
                <div className="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]">
                    <IdentityCard profile={profile} />
                    <div className="flex flex-col gap-6">
                        <ProfileForm profile={profile} />
                        <PasswordCard />
                    </div>
                </div>
            )}
        </PageContainer>
    );
}
