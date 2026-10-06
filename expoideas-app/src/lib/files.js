import { API_BASE_URL } from '@/lib/apiClient';
import { session } from '@/lib/session';

/**
 * Archivos de la plataforma. Las mismas reglas las aplica la API (FileService y
 * FileFormat): aquí solo evitan subir algo que se va a rechazar. La API decide
 * por el contenido real; el navegador solo ve el tipo declarado.
 */

/** Nombre de la parte multipart del archivo en las subidas. */
export const FILE_PART = 'file';

/** Límite de TI por archivo. */
export const MAX_FILE_BYTES = 5 * 1024 * 1024;

export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** Plantillas de entregables: lo que el selector de archivos ofrece. La API decide por el contenido. */
export const TEMPLATE_ACCEPT = '.pdf,.docx,.pptx';

/** URL para mostrar o descargar un archivo por su identificador; null si no hay. */
export const fileUrl = (id) => (id ? `${API_BASE_URL}/files/${encodeURIComponent(id)}` : null);

/** Mensaje de error si el archivo está vacío o pasa del límite, o null si cabe. */
export const validateSize = (file) => {
    if (!file) return 'Elige un archivo.';
    if (file.size === 0) return 'El archivo está vacío.';
    if (file.size > MAX_FILE_BYTES) return 'El archivo supera el tamaño máximo permitido de 5 MB.';
    return null;
};

/** Mensaje de error si la imagen no se puede subir, o null si parece válida. */
export const validateImage = (file) => {
    if (!file) return 'Elige una imagen.';
    if (!IMAGE_TYPES.includes(file.type)) return 'Formato no permitido. Usa un archivo JPG, PNG o WEBP.';
    if (file.size === 0) return 'El archivo está vacío.';
    if (file.size > MAX_FILE_BYTES) return 'El archivo supera el tamaño máximo permitido de 5 MB.';
    return null;
};

/**
 * Descarga un archivo privado (un entregable). Un enlace normal no sirve: el
 * navegador no le pondría el token de la sesión, y la API responde 404 a quien
 * no puede verlo.
 */
export const downloadFile = async (id, name) => {
    const response = await fetch(fileUrl(id), { headers: { Authorization: `Bearer ${session.token()}` } });
    if (!response.ok) {
        throw new Error('No pudimos descargar el archivo.');
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
};
