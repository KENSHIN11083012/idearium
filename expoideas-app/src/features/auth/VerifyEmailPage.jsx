import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { ROUTES, startRouteFor } from '@/lib/routes';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { AuthHeading } from '@/components/layout/AuthHeading';
import { Button } from '@/components/ui/button';
import { Alert, Spinner } from '@/components/ui/feedback';
import { authApi } from './api';
import { linkToken } from './linkToken';
import { useAuth } from './useAuth';

/**
 * Adonde lleva el enlace de verificación del correo. El token viene en el
 * fragmento de la dirección (lo que va tras el #), que el navegador no envía al
 * servidor: aquí se lee y se canjea.
 */
export default function VerifyEmailPage() {
    const { hash } = useLocation();
    const token = linkToken(hash);
    const { token: session, role, pendingSteps, completeStep } = useAuth();
    const [result, setResult] = useState(token ? { status: 'checking' } : { status: 'missing' });
    // El enlace sirve una sola vez: aunque el efecto se repita, solo se canjea una.
    const sent = useRef(false);

    useEffect(() => {
        if (!token || sent.current) return;
        sent.current = true;
        authApi
            .verifyEmail(token)
            .then(() => {
                // Si la sesión está abierta en este navegador, el paso deja de estar pendiente.
                completeStep('VERIFY_EMAIL');
                setResult({ status: 'done' });
            })
            .catch((error) => setResult({ status: 'failed', message: error.message }));
    }, [token, completeStep]);

    const next = session
        ? { to: startRouteFor(role, pendingSteps), label: 'Continuar' }
        : { to: ROUTES.LOGIN, label: 'Iniciar sesión' };

    return (
        <AuthLayout
            title="Verificar correo"
            panelTitle="Un paso para proteger tu cuenta"
            panelText="Verificar el correo confirma que la cuenta es tuya y de nadie más."
        >
            <AuthHeading eyebrow="Verificación" title="Verifica tu correo" />

            <div className="mt-8 flex flex-col gap-5">
                {result.status === 'checking' && <Spinner className="py-8" />}

                {result.status === 'done' && (
                    <>
                        <Alert variant="success" title="Tu correo quedó verificado">
                            Ya puedes seguir con tu cuenta.
                        </Alert>
                        <Button asChild size="lg" className="w-full">
                            <Link to={next.to}>
                                {next.label} <ArrowRight />
                            </Link>
                        </Button>
                    </>
                )}

                {(result.status === 'failed' || result.status === 'missing') && (
                    <>
                        <Alert variant="error" title="No pudimos verificar tu correo">
                            {result.message ?? 'El enlace está incompleto. Ábrelo de nuevo desde el correo que te enviamos.'}
                        </Alert>
                        <p className="text-sm text-on-surface-variant">
                            Inicia sesión y pide un enlace nuevo desde la pantalla de primer ingreso.
                        </p>
                        <Button asChild variant="outline" size="lg" className="w-full">
                            <Link to={session ? ROUTES.ONBOARDING : ROUTES.LOGIN}>
                                {session ? 'Volver al primer ingreso' : 'Iniciar sesión'}
                            </Link>
                        </Button>
                    </>
                )}
            </div>
        </AuthLayout>
    );
}
