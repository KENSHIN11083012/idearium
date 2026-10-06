package co.edu.unisimon.expoideas.files;

/**
 * Permiso para abrir un archivo privado que aporta el módulo dueño del archivo.
 *
 * <p>El módulo de archivos sabe de propietarios y de gestión, pero no de
 * proyectos ni de jurados: cada módulo implementa su regla y FileService las
 * consulta. Si alguna dice que sí, la descarga procede.
 */
public interface PrivateFileAccessRule {

    /**
     * @param file  archivo privado que se quiere abrir
     * @param email correo de la cuenta que lo pide
     */
    boolean canRead(StoredFile file, String email);
}
