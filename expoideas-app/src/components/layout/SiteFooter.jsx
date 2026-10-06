import { Link } from 'react-router-dom';
import { ArrowUpRight } from 'lucide-react';
import { useAuth } from '@/features/auth/useAuth';
import { ROUTES } from '@/lib/routes';
import { BrandLogo } from '@/components/brand/BrandLogo';
import { InstitutionalLogos } from '@/components/brand/InstitutionalLogos';

/**
 * La versión de la app que está corriendo: la etiqueta de la imagen publicada
 * (el commit). Es lo primero que se pregunta cuando alguien reporta un fallo.
 * En desarrollo, o compilada fuera de la integración continua, es «local».
 */
const APP_VERSION = import.meta.env.VITE_APP_VERSION || 'local';

/**
 * Pie de página institucional. Solo enlaces que existen: las secciones de
 * INNPRENDE I y II se agregan cuando existan sus pantallas.
 */
export function SiteFooter() {
    const { token } = useAuth();
    const year = new Date().getFullYear();

    return (
        <footer className="mt-auto border-t border-outline-variant/70 bg-surface-container-lowest">
            <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-[1.5fr_1fr_1fr] lg:px-8">
                <div className="flex flex-col gap-4">
                    <BrandLogo size="sm" />
                    <p className="max-w-sm text-sm leading-relaxed text-on-surface-variant">
                        Los proyectos de la Cátedra UNISIMÓN INNPRENDE de la Universidad Simón Bolívar, con el acompañamiento de
                        MacondoLab.
                    </p>
                    <InstitutionalLogos />
                </div>

                <nav className="flex flex-col gap-3" aria-label="Plataforma">
                    <h2 className="label-mono text-on-surface">Plataforma</h2>
                    <ul className="flex flex-col gap-2 text-sm text-on-surface-variant">
                        <li>
                            <Link className="hover:text-primary" to={ROUTES.HOME}>
                                Inicio
                            </Link>
                        </li>
                        {token ? (
                            <>
                                <li>
                                    <Link className="hover:text-primary" to={ROUTES.PROFILE}>
                                        Mi perfil
                                    </Link>
                                </li>
                                <li>
                                    <Link className="hover:text-primary" to={ROUTES.SECURITY}>
                                        Seguridad
                                    </Link>
                                </li>
                            </>
                        ) : (
                            <>
                                <li>
                                    <Link className="hover:text-primary" to={ROUTES.REGISTER}>
                                        Crear cuenta
                                    </Link>
                                </li>
                                <li>
                                    <Link className="hover:text-primary" to={ROUTES.LOGIN}>
                                        Iniciar sesión
                                    </Link>
                                </li>
                            </>
                        )}
                    </ul>
                </nav>

                <nav className="flex flex-col gap-3" aria-label="Institucional">
                    <h2 className="label-mono text-on-surface">Institucional</h2>
                    <ul className="flex flex-col gap-2 text-sm text-on-surface-variant">
                        <li>
                            <a
                                className="inline-flex items-center gap-1 hover:text-primary"
                                href="https://www.unisimon.edu.co"
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                Universidad Simón Bolívar <ArrowUpRight className="size-3.5" aria-hidden="true" />
                            </a>
                        </li>
                    </ul>
                </nav>
            </div>

            <div className="border-t border-outline-variant/60">
                <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-5 text-xs text-on-surface-variant sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
                    <p>© {year} Universidad Simón Bolívar. Todos los derechos reservados.</p>
                    <p className="font-mono uppercase tracking-wider">
                        Vigilada Mineducación · Barranquilla y Cúcuta ·{' '}
                        {/* En minúsculas, como es: la etiqueta se copia tal cual para buscar el commit. */}
                        <span title="Versión de la plataforma" className="normal-case">
                            v. {APP_VERSION}
                        </span>
                    </p>
                </div>
            </div>
        </footer>
    );
}
