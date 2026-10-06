import { Compass } from 'lucide-react';
import { StatusPage } from '@/components/layout/StatusPage';

export default function NotFoundPage() {
    return (
        <StatusPage
            code="Error 404 · Página no encontrada"
            icon={Compass}
            title="Esta página no existe"
            description="Puede que el enlace esté mal escrito o que la página se haya movido."
        />
    );
}
