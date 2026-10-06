/** Encabezado de las pantallas de acceso: etiqueta técnica, título y texto. */
export function AuthHeading({ eyebrow, title, children }) {
    return (
        <div className="flex flex-col gap-2">
            <p className="label-mono text-primary">{eyebrow}</p>
            <h1 className="font-heading text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
            {children && <p className="text-on-surface-variant">{children}</p>}
        </div>
    );
}
