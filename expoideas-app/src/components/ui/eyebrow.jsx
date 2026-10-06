import { Badge } from './badge';

/** Etiqueta técnica con punto, sobre los títulos de página y de sección. */
export function Eyebrow({ children }) {
    return (
        <Badge variant="primary" mono className="self-start whitespace-normal">
            <span className="size-1.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />
            {children}
        </Badge>
    );
}
