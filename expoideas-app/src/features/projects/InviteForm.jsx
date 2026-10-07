import { useDeferredValue, useState } from 'react';
import { toast } from 'sonner';
import { UserPlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { MIN_CANDIDATE_SEARCH, useInvitationCandidates, useInviteMember } from './queries';
import { invitationSchema } from './schemas';

/** De dónde es, para distinguir a dos personas que se llaman igual. */
const origin = (candidate) => [candidate.academicProgram ?? candidate.faculty, candidate.campus].filter(Boolean).join(' · ');

/**
 * Invitar a un compañero, que solo lo hace el líder. Se le busca por su nombre
 * y se elige de la lista, o se escribe su correo completo: con una arroba, lo
 * escrito se toma como correo y no se busca.
 */
export function InviteForm({ projectId, disabled }) {
    const invite = useInviteMember(projectId);
    const [text, setText] = useState('');
    const [error, setError] = useState('');
    const term = text.trim();
    const byEmail = term.includes('@');
    const searchable = !byEmail && term.length >= MIN_CANDIDATE_SEARCH;
    // Se busca con lo último que quedó escrito, no con cada tecla.
    const search = useDeferredValue(searchable ? term : '');
    const { data: candidates = [], isPending, error: searchError } = useInvitationCandidates(projectId, search);

    const send = async (body, name) => {
        setError('');
        try {
            await invite.mutateAsync(body);
            toast.success(`Invitamos a ${name}`);
            setText('');
        } catch (inviteError) {
            // La API atribuye al mismo campo lo que impide invitar, se haya elegido de la lista o escrito el correo.
            setError(inviteError.body?.fields?.email ?? inviteError.message);
        }
    };

    const submit = (event) => {
        event.preventDefault();
        if (!byEmail) {
            setError(
                searchable
                    ? 'Elige a tu compañero en la lista, o escribe su correo completo.'
                    : `Escribe al menos ${MIN_CANDIDATE_SEARCH} letras de su nombre, o su correo completo.`,
            );
            return;
        }
        const parsed = invitationSchema.safeParse({ email: term });
        if (!parsed.success) {
            setError(parsed.error.issues[0].message);
            return;
        }
        send({ email: parsed.data.email }, parsed.data.email);
    };

    const change = (event) => {
        setText(event.target.value);
        setError('');
    };

    return (
        <div className="flex flex-col gap-3">
            <form onSubmit={submit} noValidate className="flex flex-col gap-3 sm:flex-row sm:items-start">
                <Field
                    label="Invitar a un compañero"
                    error={error || undefined}
                    hint="Búscalo por su nombre o escribe su correo institucional. Debe tener cuenta en Idearium."
                    className="flex-1"
                >
                    <Input autoComplete="off" placeholder="Nombre o correo" value={text} onChange={change} />
                </Field>
                <Button type="submit" loading={invite.isPending} disabled={disabled} className="sm:mt-6">
                    <UserPlus /> Invitar
                </Button>
            </form>

            {searchable && search === term && (
                <div aria-live="polite">
                    {searchError ? (
                        <p className="text-sm text-error">{searchError.message}</p>
                    ) : isPending ? (
                        <p className="text-sm text-on-surface-variant">Buscando…</p>
                    ) : candidates.length === 0 ? (
                        <p className="text-sm text-on-surface-variant">
                            Nadie con ese nombre tiene cuenta para invitar. Si ya se registró, prueba con su correo.
                        </p>
                    ) : (
                        <ul aria-label="Compañeros encontrados" className="flex flex-col gap-2">
                            {candidates.map((candidate) => (
                                <li
                                    key={candidate.id}
                                    className="flex items-center justify-between gap-3 rounded border border-outline-variant/60 px-4 py-2"
                                >
                                    <div className="flex min-w-0 flex-col">
                                        <p className="font-medium text-on-surface">{candidate.fullName}</p>
                                        <p className="truncate text-xs text-on-surface-variant">
                                            {[origin(candidate), candidate.email].filter(Boolean).join(' · ')}
                                        </p>
                                    </div>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        disabled={disabled || invite.isPending}
                                        onClick={() => send({ userId: candidate.id }, candidate.fullName)}
                                        aria-label={`Invitar a ${candidate.fullName}`}
                                    >
                                        <UserPlus /> Invitar
                                    </Button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}
