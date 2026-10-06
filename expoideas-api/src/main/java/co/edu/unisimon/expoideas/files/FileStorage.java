package co.edu.unisimon.expoideas.files;

import java.util.NoSuchElementException;
import org.springframework.core.io.Resource;

/**
 * Dónde vive el contenido de los archivos. Hoy es una carpeta local; si TI
 * ofrece otro almacenamiento (p. ej. compatible con S3), basta otra
 * implementación sin tocar los servicios.
 *
 * <p>Las rutas las genera la plataforma ({@code aaaa/mm/uuid.ext}), nunca el
 * usuario.
 */
public interface FileStorage {

    void save(String path, byte[] content);

    /** @throws NoSuchElementException si no hay contenido en esa ruta */
    Resource open(String path);

    /** No falla si ya no existe. */
    void delete(String path);
}
