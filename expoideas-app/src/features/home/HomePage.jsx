import { Link } from 'react-router-dom';
import { ArrowRight, CircleCheck, GraduationCap, Handshake, Lightbulb, Rocket, Scale, UserRound } from 'lucide-react';
import { useAuth } from '@/features/auth/useAuth';
import { ROUTES } from '@/lib/routes';
import { INSTITUTIONAL_DOMAIN } from '@/lib/validation';
import { InstitutionalLogos } from '@/components/brand/InstitutionalLogos';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Eyebrow } from '@/components/ui/eyebrow';
import heroPhoto from '@/assets/brand/login-hero.webp';

/*
 * Portada pública. Contenido estático: la galería, los rankings y las cifras de
 * los diseños llegan con las fases del dominio, cuando haya datos reales.
 */

// Cada fase tiene su propia inscripción: un proyecto no pasa solo de una a otra.
const PHASES = [
    {
        phase: 'INNPRENDE I',
        name: 'Despegue',
        accent: 'primary',
        icon: Lightbulb,
        title: 'Esboza tu idea de emprendimiento',
        text: 'Durante el semestre el equipo investiga hasta llegar a su idea de negocio y la presenta en un póster de investigación.',
        points: [
            'Inscripción del proyecto con tu equipo',
            'Entrega del póster de investigación',
            'Evaluación por jurados y ranking',
        ],
    },
    {
        phase: 'INNPRENDE II',
        name: 'Aterrizaje',
        accent: 'lime',
        icon: Rocket,
        title: 'Convierte la idea en un prototipo',
        text: 'La idea se vuelve una versión mínima del producto, se valida y se presenta en un pitch comercial ante jurados externos.',
        points: [
            'Prototipo y evidencias de validación',
            'Pitch comercial ante jurados externos',
            'Evaluación, comentarios y ranking',
        ],
    },
];

const AUDIENCES = [
    { icon: UserRound, title: 'Estudiantes', text: 'Inscriben sus proyectos, suben sus entregables y consultan su evaluación.' },
    { icon: GraduationCap, title: 'Profesores', text: 'Acompañan a sus grupos y siguen sus resultados.' },
    { icon: Scale, title: 'Jurados', text: 'Evalúan los proyectos que tienen asignados con criterios comunes.' },
    { icon: Handshake, title: 'MacondoLab', text: 'Coordina las fases, valida a los jurados y publica los resultados.' },
];

function MainActions({ signedIn, management }) {
    if (signedIn) {
        return (
            <div className="flex flex-wrap gap-3">
                <Button asChild size="lg">
                    <Link to={ROUTES.PROFILE}>
                        Ir a mi perfil <ArrowRight />
                    </Link>
                </Button>
                {management && (
                    <Button asChild size="lg" variant="outline">
                        <Link to={ROUTES.USERS}>Administración</Link>
                    </Button>
                )}
            </div>
        );
    }
    return (
        <div className="flex flex-col gap-3">
            <div className="flex flex-wrap gap-3">
                <Button asChild size="lg">
                    <Link to={ROUTES.REGISTER}>
                        Crear cuenta <ArrowRight />
                    </Link>
                </Button>
                <Button asChild size="lg" variant="outline">
                    <Link to={ROUTES.LOGIN}>Iniciar sesión</Link>
                </Button>
            </div>
            <p className="label-mono font-normal text-on-surface-variant">Acceso con correo {INSTITUTIONAL_DOMAIN}</p>
        </div>
    );
}

function PhaseCard({ phase, name, accent, icon: Icon, title, text, points, connector }) {
    const lime = accent === 'lime';

    return (
        <>
            <Card accent={accent} className="flex flex-col gap-5 p-6 sm:p-8">
                {/* Las dos etiquetas no caben juntas en un celular: la segunda baja de línea. */}
                <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
                    <Badge variant={lime ? 'lime' : 'primary'} mono>
                        {phase} · {name}
                    </Badge>
                    <Badge variant="outline" mono>
                        Próximamente
                    </Badge>
                </div>
                <div className="flex items-start gap-4">
                    <span
                        className={
                            lime
                                ? 'flex size-12 shrink-0 items-center justify-center rounded-lg bg-secondary-container text-on-secondary-fixed'
                                : 'flex size-12 shrink-0 items-center justify-center rounded-lg bg-primary text-on-primary'
                        }
                    >
                        <Icon className="size-6" aria-hidden="true" />
                    </span>
                    <div className="flex flex-col gap-2">
                        <h3 className="font-heading text-xl font-bold">{title}</h3>
                        <p className="text-on-surface-variant">{text}</p>
                    </div>
                </div>
                <ul className="mt-auto flex flex-col gap-2.5 border-t border-outline-variant/50 pt-5">
                    {points.map((point) => (
                        <li key={point} className="flex items-start gap-2.5 text-sm">
                            <CircleCheck
                                className={lime ? 'mt-0.5 size-4 shrink-0 text-secondary' : 'mt-0.5 size-4 shrink-0 text-primary'}
                                aria-hidden="true"
                            />
                            {point}
                        </li>
                    ))}
                </ul>
            </Card>
            {connector && (
                <div className="flex items-center justify-center" aria-hidden="true">
                    <span className="flex size-11 items-center justify-center rounded-full border border-outline-variant bg-surface-container-lowest text-primary shadow-soft">
                        <ArrowRight className="size-5 rotate-90 lg:rotate-0" />
                    </span>
                </div>
            )}
        </>
    );
}

export default function HomePage() {
    const { token, isManagement } = useAuth();
    const signedIn = Boolean(token);

    return (
        <>
            <title>Idearium · Universidad Simón Bolívar</title>

            {/* Portada */}
            <section className="relative overflow-hidden border-b border-outline-variant/60 bg-surface-container-lowest">
                <div
                    className="tech-grid absolute inset-0 [mask-image:linear-gradient(to_bottom,black,transparent)]"
                    aria-hidden="true"
                />
                <div
                    className="absolute -right-40 -top-40 size-[32rem] rounded-full bg-secondary-container/30 blur-3xl"
                    aria-hidden="true"
                />

                <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-4 py-14 sm:px-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:px-8 lg:py-24">
                    <div className="flex min-w-0 flex-col gap-6">
                        <Eyebrow>Universidad Simón Bolívar · Barranquilla y Cúcuta</Eyebrow>
                        <h1 className="font-heading text-4xl font-extrabold leading-[1.05] tracking-tight text-on-surface sm:text-5xl xl:text-6xl">
                            Las ideas de la Unisimón,{' '}
                            <span className="text-primary underline decoration-secondary-container decoration-[6px] underline-offset-[6px]">
                                a la vista de todos
                            </span>
                        </h1>
                        <p className="max-w-xl text-lg leading-relaxed text-on-surface-variant">
                            Idearium reúne los proyectos de la Cátedra INNPRENDE: los equipos los inscriben con sus entregables,
                            los jurados los evalúan y los resultados quedan a la vista de la comunidad.
                        </p>
                        <MainActions signedIn={signedIn} management={isManagement} />
                    </div>

                    <figure className="relative">
                        <div className="overflow-hidden rounded-lg border border-outline-variant/70 shadow-hard">
                            <img
                                src={heroPhoto}
                                alt="Estudiantes de la Universidad Simón Bolívar en un evento de emprendimiento"
                                className="aspect-[4/3] w-full object-cover"
                                fetchPriority="high"
                            />
                        </div>
                        <figcaption className="glass absolute -bottom-6 left-4 right-4 flex flex-col gap-3 rounded-lg border border-outline-variant/70 p-4 shadow-lg sm:left-auto sm:right-6 sm:w-80">
                            <p className="label-mono text-[11px] font-normal sm:text-[10px] text-on-surface-variant">
                                Con el respaldo de
                            </p>
                            <InstitutionalLogos />
                        </figcaption>
                    </figure>
                </div>
            </section>

            {/* Ruta en dos fases */}
            <section className="mx-auto flex max-w-7xl flex-col gap-10 px-4 py-20 sm:px-6 lg:px-8" aria-labelledby="phases-title">
                <div className="flex max-w-2xl flex-col gap-3">
                    <Eyebrow>Cátedra UNISIMÓN INNPRENDE</Eyebrow>
                    <h2 id="phases-title" className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">
                        De la investigación al prototipo, en dos fases
                    </h2>
                    <p className="text-on-surface-variant">
                        Cada fase tiene su propia inscripción y su propia evaluación por jurados. Los proyectos, sus entregables y
                        sus resultados quedan guardados como evidencia del proceso.
                    </p>
                </div>

                <div className="grid grid-cols-1 items-stretch gap-6 lg:grid-cols-[1fr_auto_1fr]">
                    {PHASES.map((phase, index) => (
                        <PhaseCard key={phase.phase} {...phase} connector={index === 0} />
                    ))}
                </div>
            </section>

            {/* Para quién */}
            <section className="border-y border-outline-variant/60 bg-surface-container-low" aria-labelledby="audiences-title">
                <div className="mx-auto flex max-w-7xl flex-col gap-10 px-4 py-20 sm:px-6 lg:px-8">
                    <div className="flex max-w-2xl flex-col gap-3">
                        <Eyebrow>Comunidad</Eyebrow>
                        <h2 id="audiences-title" className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">
                            Un espacio para todo el ecosistema
                        </h2>
                    </div>
                    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                        {AUDIENCES.map(({ icon: Icon, title, text }) => (
                            <li key={title}>
                                <Card className="flex h-full flex-col gap-4 p-6 transition-shadow hover:shadow-hard-sm">
                                    <span className="flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
                                        <Icon className="size-5" aria-hidden="true" />
                                    </span>
                                    <div className="flex flex-col gap-1.5">
                                        <h3 className="font-heading text-lg font-semibold">{title}</h3>
                                        <p className="text-sm text-on-surface-variant">{text}</p>
                                    </div>
                                </Card>
                            </li>
                        ))}
                    </ul>
                </div>
            </section>

            {/* Llamado a la acción */}
            <section className="relative overflow-hidden bg-primary" aria-labelledby="cta-title">
                <div className="tech-grid-light absolute inset-0" aria-hidden="true" />
                <div className="relative mx-auto flex max-w-7xl flex-col items-start justify-between gap-8 px-4 py-16 sm:px-6 lg:flex-row lg:items-center lg:px-8">
                    <div className="flex max-w-2xl flex-col gap-3">
                        <h2 id="cta-title" className="font-heading text-3xl font-bold tracking-tight text-on-primary sm:text-4xl">
                            {signedIn ? 'Tu cuenta está lista' : '¿Cursas INNPRENDE I o II?'}
                        </h2>
                        <p className="text-on-primary-container">
                            {signedIn
                                ? 'Revisa que tus datos estén al día para cuando se abran las inscripciones.'
                                : 'Crea tu cuenta con el correo institucional y prepárate para inscribir tu proyecto.'}
                        </p>
                    </div>
                    <Button asChild size="lg" variant="lime">
                        <Link to={signedIn ? ROUTES.PROFILE : ROUTES.REGISTER}>
                            {signedIn ? 'Revisar mi perfil' : 'Crear mi cuenta'} <ArrowRight />
                        </Link>
                    </Button>
                </div>
            </section>
        </>
    );
}
