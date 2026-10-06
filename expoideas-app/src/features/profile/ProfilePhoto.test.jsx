import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { MAX_FILE_BYTES, fileUrl } from '@/lib/files';
import { apiError, createTestQueryClient } from '@/test/utils';
import { ProfilePhoto } from './ProfilePhoto';
import { profileApi } from './api';
import { useProfile } from './queries';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn(() => ({ updateUser: vi.fn() })) }));
vi.mock('./api', () => ({ profileApi: { get: vi.fn(), update: vi.fn(), uploadPhoto: vi.fn(), deletePhoto: vi.fn() } }));

const PHOTO_ID = '0b0f3f7e-8c1a-4c55-9d3e-2f1a6b7c8d9e';

/** Como en ProfilePage: la foto nueva se refleja al instante a través de la caché de la consulta. */
function WithProfile() {
    const { data: profile } = useProfile();
    return profile ? <ProfilePhoto profile={profile} /> : null;
}

function renderPhoto(profile = { firstName: 'Ana', lastName: 'Pérez', photoId: null }) {
    profileApi.get.mockResolvedValue(profile);
    render(
        <QueryClientProvider client={createTestQueryClient()}>
            <WithProfile />
        </QueryClientProvider>,
    );
}

const image = (name, type, size = 1024) => new File([new Uint8Array(size)], name, { type });

describe('Foto de perfil', () => {
    it('sin foto muestra las iniciales y la opción de subir una', async () => {
        renderPhoto();

        expect(await screen.findByText('AP')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Subir foto/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Quitar/ })).not.toBeInTheDocument();
    });

    it('sube la imagen como formulario multipart y la muestra', async () => {
        const user = userEvent.setup();
        profileApi.uploadPhoto.mockResolvedValue({ firstName: 'Ana', lastName: 'Pérez', photoId: PHOTO_ID });
        renderPhoto();
        const photo = image('yo.png', 'image/png');

        await user.upload(await screen.findByLabelText('Elegir foto de perfil'), photo);

        expect(profileApi.uploadPhoto).toHaveBeenCalledWith(photo, expect.anything());
        expect(await screen.findByRole('img', { name: 'Ana Pérez' })).toHaveAttribute('src', fileUrl(PHOTO_ID));
        expect(screen.getByRole('button', { name: /Cambiar foto/ })).toBeInTheDocument();
    });

    it('no sube formatos que la API va a rechazar', async () => {
        // Sin filtrar por "accept", para simular a quien elige "Todos los archivos".
        const user = userEvent.setup({ applyAccept: false });
        renderPhoto();

        await user.upload(await screen.findByLabelText('Elegir foto de perfil'), image('poster.pdf', 'application/pdf'));

        expect(screen.getByRole('alert')).toHaveTextContent('Formato no permitido. Usa un archivo JPG, PNG o WEBP.');
        expect(profileApi.uploadPhoto).not.toHaveBeenCalled();
    });

    it('no sube imágenes de más de 5 MB', async () => {
        const user = userEvent.setup();
        renderPhoto();

        await user.upload(
            await screen.findByLabelText('Elegir foto de perfil'),
            image('grande.jpg', 'image/jpeg', MAX_FILE_BYTES + 1),
        );

        expect(screen.getByRole('alert')).toHaveTextContent('El archivo supera el tamaño máximo permitido de 5 MB.');
        expect(profileApi.uploadPhoto).not.toHaveBeenCalled();
    });

    it('muestra el motivo si la API rechaza el contenido', async () => {
        const user = userEvent.setup();
        profileApi.uploadPhoto.mockRejectedValue(
            apiError('Datos inválidos', 400, { fields: { file: 'Formato no permitido. Usa un archivo JPG, PNG o WEBP.' } }),
        );
        renderPhoto();

        // Un archivo que dice ser PNG pero no lo es: solo la API lo puede saber.
        await user.upload(await screen.findByLabelText('Elegir foto de perfil'), image('falsa.png', 'image/png'));

        expect(await screen.findByRole('alert')).toHaveTextContent('Formato no permitido. Usa un archivo JPG, PNG o WEBP.');
    });

    it('quita la foto', async () => {
        const user = userEvent.setup();
        profileApi.deletePhoto.mockResolvedValue(null);
        renderPhoto({ firstName: 'Ana', lastName: 'Pérez', photoId: PHOTO_ID });

        await user.click(await screen.findByRole('button', { name: /Quitar/ }));

        expect(profileApi.deletePhoto).toHaveBeenCalled();
        expect(await screen.findByText('AP')).toBeInTheDocument();
    });
});
