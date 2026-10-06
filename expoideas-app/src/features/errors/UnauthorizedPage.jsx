import { ShieldOff } from 'lucide-react';
import { StatusPage } from '@/components/layout/StatusPage';

export default function UnauthorizedPage() {
    return (
        <StatusPage
            code="Error 403 · Acceso restringido"
            icon={ShieldOff}
            title="No tienes acceso a esta sección"
            description="Tu cuenta no tiene los permisos necesarios. Si crees que es un error, comunícate con la administración de la plataforma."
        />
    );
}
