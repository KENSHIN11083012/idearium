import { Component } from 'react';
import { RefreshCw, RotateCw, TriangleAlert } from 'lucide-react';
import { isStaleChunkError, reloadForNewVersion } from '@/lib/staleChunk';
import { Button } from '@/components/ui/button';

/** El inicio de la app, con su subdirectorio. */
const HOME = import.meta.env.BASE_URL;

const MESSAGES = {
    error: {
        icon: TriangleAlert,
        title: 'Algo salió mal',
        description: 'Esta página tuvo un problema inesperado. Lo que ya guardaste sigue a salvo: recarga para continuar.',
    },
    newVersion: {
        icon: RefreshCw,
        title: 'Hay una versión nueva de Idearium',
        description: 'La plataforma se actualizó mientras tenías esta pestaña abierta. Recarga la página para seguir.',
    },
};

/**
 * Atrapa lo que falla al pintar una página para que la app no quede en blanco.
 * Si lo que falló es la descarga de un archivo de la versión anterior, recarga
 * sola una vez (lib/staleChunk.js).
 *
 * @param {string} [resetKey] al cambiar (p. ej. la ruta), se quita el aviso y se vuelve a intentar.
 * @param {() => void} [reload] cómo recargar; por defecto, la página entera.
 */
export class ErrorBoundary extends Component {
    state = { failure: null };

    static getDerivedStateFromError(error) {
        return { failure: isStaleChunkError(error) ? 'newVersion' : 'error' };
    }

    componentDidCatch(error) {
        if (isStaleChunkError(error)) reloadForNewVersion(this.reload);
    }

    componentDidUpdate(previous) {
        if (this.state.failure && previous.resetKey !== this.props.resetKey) this.setState({ failure: null });
    }

    reload = () => (this.props.reload ?? (() => window.location.reload()))();

    render() {
        if (!this.state.failure) return this.props.children;

        const { icon: Icon, title, description } = MESSAGES[this.state.failure];
        // Sin enlaces del router: este aviso también se pinta cuando lo que falló está por encima de él.
        return (
            <section role="alert" className="tech-grid flex min-h-[70vh] items-center justify-center px-4 py-16">
                <div className="flex max-w-md flex-col items-center gap-5 text-center">
                    <span className="flex size-16 items-center justify-center rounded-lg border border-outline-variant bg-surface-container-lowest text-primary shadow-hard">
                        <Icon className="size-8" aria-hidden="true" />
                    </span>
                    <h1 className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
                    <p className="text-on-surface-variant">{description}</p>
                    <div className="mt-2 flex flex-wrap items-center justify-center gap-3">
                        <Button size="lg" onClick={this.reload}>
                            <RotateCw /> Recargar la página
                        </Button>
                        <Button size="lg" variant="outline" asChild>
                            <a href={HOME}>Ir al inicio</a>
                        </Button>
                    </div>
                </div>
            </section>
        );
    }
}
