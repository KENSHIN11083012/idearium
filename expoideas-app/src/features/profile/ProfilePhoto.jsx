import { useRef, useState } from 'react';
import { toast } from 'sonner';
import { Camera, Trash2 } from 'lucide-react';
import { FILE_PART, IMAGE_TYPES, fileUrl, validateImage } from '@/lib/files';
import { Avatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { useDeletePhoto, useUploadPhoto } from './queries';

/**
 * Foto de perfil con acciones para cambiarla o quitarla. La imagen se valida
 * antes de subir (formato y 5 MB) y la API la vuelve a validar por su contenido.
 *
 * @param {object} profile  la cuenta, como la devuelve /users/me
 */
export function ProfilePhoto({ profile }) {
    const picker = useRef(null);
    const [error, setError] = useState('');
    const upload = useUploadPhoto();
    const remove = useDeletePhoto();
    const busy = upload.isPending || remove.isPending;

    const onPick = (event) => {
        const file = event.target.files?.[0];
        // Permite volver a elegir el mismo archivo después de un error.
        event.target.value = '';
        if (!file) return;

        const problem = validateImage(file);
        if (problem) {
            setError(problem);
            return;
        }
        setError('');
        upload.mutate(file, {
            onSuccess: () => toast.success('Tu foto se actualizó'),
            onError: (uploadError) => setError(uploadError.body?.fields?.[FILE_PART] ?? uploadError.message),
        });
    };

    const onRemove = () => {
        setError('');
        remove.mutate(undefined, {
            onSuccess: () => toast.success('Quitaste tu foto'),
            onError: (removeError) => setError(removeError.message),
        });
    };

    return (
        <div className="flex flex-col items-center gap-3">
            <Avatar firstName={profile.firstName} lastName={profile.lastName} photoUrl={fileUrl(profile.photoId)} size="lg" />
            <input
                ref={picker}
                type="file"
                accept={IMAGE_TYPES.join(',')}
                onChange={onPick}
                className="sr-only"
                aria-label="Elegir foto de perfil"
                tabIndex={-1}
            />
            <div className="flex flex-wrap justify-center gap-2">
                <Button type="button" variant="outline" size="sm" loading={busy} onClick={() => picker.current?.click()}>
                    <Camera /> {profile.photoId ? 'Cambiar foto' : 'Subir foto'}
                </Button>
                {profile.photoId && (
                    <Button type="button" variant="ghost" size="sm" disabled={busy} onClick={onRemove}>
                        <Trash2 /> Quitar
                    </Button>
                )}
            </div>
            {error ? (
                <p role="alert" className="max-w-56 text-xs font-medium text-error">
                    {error}
                </p>
            ) : (
                <p className="text-xs text-on-surface-variant">JPG, PNG o WEBP de hasta 5 MB.</p>
            )}
        </div>
    );
}
