import { Building2, CalendarDays, GraduationCap, Landmark, Mail } from 'lucide-react';
import { requiresAffiliation, roleLabel } from '@/lib/roles';
import { fullName } from '@/lib/text';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { ProfilePhoto } from './ProfilePhoto';

const longDate = (iso) =>
    iso ? new Date(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' }) : null;

function Detail({ icon: Icon, label, value, empty = 'Sin asignar' }) {
    return (
        <div className="flex items-start gap-3 py-3">
            <Icon className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
            <div className="min-w-0">
                <dt className="label-mono text-[11px] font-normal sm:text-[10px] text-on-surface-variant">{label}</dt>
                <dd className={value ? 'break-words text-sm text-on-surface' : 'text-sm italic text-outline'}>
                    {value ?? empty}
                </dd>
            </div>
        </div>
    );
}

/** Foto, nombre, rol y datos de la cuenta. */
export function IdentityCard({ profile }) {
    return (
        <Card accent="primary" className="h-fit">
            <CardContent className="flex flex-col items-center gap-3 pt-8 text-center">
                <ProfilePhoto profile={profile} />
                <div className="flex flex-col items-center gap-2">
                    <p className="font-heading text-xl font-bold leading-tight">
                        {fullName(profile.firstName, profile.lastName)}
                    </p>
                    <Badge variant="lime" mono>
                        {roleLabel(profile.role)}
                    </Badge>
                </div>
            </CardContent>
            <dl className="mx-6 divide-y divide-outline-variant/50 border-t border-outline-variant/50 pb-2">
                <Detail icon={Mail} label="Correo institucional" value={profile.email} />
                {requiresAffiliation(profile.role) && (
                    <>
                        <Detail icon={Landmark} label="Sede" value={profile.campus} />
                        <Detail icon={Building2} label="Facultad" value={profile.faculty} />
                        <Detail
                            icon={GraduationCap}
                            label="Programa académico"
                            value={profile.academicProgram}
                            empty="Sin programa"
                        />
                    </>
                )}
                <Detail icon={CalendarDays} label="Miembro desde" value={longDate(profile.createdAt)} />
            </dl>
        </Card>
    );
}
