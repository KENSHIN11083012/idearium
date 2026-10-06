package co.edu.unisimon.expoideas.files;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Archivos en una carpeta del servidor ({@code expoideas.files.directory},
 * variable FILES_DIR). Esa carpeta debe quedar fuera de lo que publica el
 * servidor web y entrar en las copias de seguridad junto con la BD.
 */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(ExpoideasProperties properties) {
        this.root = properties.files().directory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de archivos " + root, e);
        }
        log.info("Archivos en {}", root);
    }

    @Override
    public void save(String path, byte[] content) {
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            // CREATE_NEW: nunca sobrescribe; cada ruta lleva un UUID nuevo.
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    @Override
    public Resource open(String path) {
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) {
            // Metadatos sin contenido: se responde como inexistente y queda rastro para revisarlo.
            log.error("Falta el contenido del archivo {}", path);
            throw new NoSuchElementException("El archivo no existe");
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            // El registro ya no existe: un huérfano en disco no rompe nada, pero se reporta.
            log.warn("No se pudo borrar el archivo {}: {}", path, e.getMessage());
        }
    }

    /** Impide salir de la carpeta raíz aunque la ruta traiga "..". */
    private Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Ruta de archivo no válida");
        }
        return resolved;
    }
}
