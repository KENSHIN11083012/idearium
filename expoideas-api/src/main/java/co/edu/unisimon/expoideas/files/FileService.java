package co.edu.unisimon.expoideas.files;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.security.UserPrincipal;
import co.edu.unisimon.expoideas.users.User;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda, sirve y borra archivos. Los módulos (foto de perfil, entregables,
 * galería) lo usan desde sus propias transacciones y deciden qué formatos y qué
 * visibilidad corresponden.
 *
 * <p>Disco y BD no comparten transacción: si la transacción se revierte, el
 * contenido recién escrito se borra; si un borrado confirma, recién ahí se
 * borra el contenido. Así no quedan registros apuntando a nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** Límite de TI. spring.servlet.multipart.max-file-size lo aplica antes; esta es la segunda barrera. */
    static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;

    /** Campo al que se atribuyen los errores del archivo; es también el nombre de la parte multipart. */
    public static final String FIELD = "file";

    private final FileRepository fileRepository;
    private final FileStorage storage;
    private final Clock clock;

    /** Permisos que aportan otros módulos (entregables, galería...). Puede estar vacía. */
    private final List<PrivateFileAccessRule> accessRules;

    /**
     * Valida y guarda un archivo subido.
     *
     * @throws InvalidFieldsException si está vacío, pasa de 5 MB o su contenido no es de un formato permitido
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StoredFile store(MultipartFile upload, Set<FileFormat> allowed, FileVisibility visibility, User owner) {
        byte[] content = read(upload);
        if (content.length == 0) {
            throw invalid("El archivo está vacío.");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw invalid("El archivo supera el tamaño máximo permitido de 5 MB.");
        }
        FileFormat format = FileFormat.detect(content)
                .filter(allowed::contains)
                .orElseThrow(
                        () -> invalid("Formato no permitido. Usa un archivo " + FileFormat.describe(allowed) + "."));

        String uuid = UUID.randomUUID().toString();
        LocalDate today = LocalDate.now(clock);
        String path = "%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), uuid, format.extension());

        storage.save(path, content);
        afterTransaction(status -> {
            if (status != TransactionSynchronization.STATUS_COMMITTED) {
                storage.delete(path);
            }
        });

        StoredFile file = fileRepository.save(StoredFile.builder()
                .uuid(uuid)
                .originalName(safeName(upload.getOriginalFilename(), format))
                .contentType(format.contentType())
                .sizeBytes(content.length)
                .sha256(sha256(content))
                .storagePath(path)
                .visibility(visibility)
                .owner(owner)
                .build());
        log.info(
                "Archivo {} ({}, {} bytes) subido por el usuario ID {}",
                uuid,
                format.contentType(),
                content.length,
                owner.getId());
        return file;
    }

    /** Borra el registro ya y el contenido cuando la transacción confirma. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(StoredFile file) {
        String path = file.getStoragePath();
        fileRepository.delete(file);
        afterTransaction(status -> {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                storage.delete(path);
            }
        });
    }

    /** Para eliminar una cuenta: sus archivos no pueden quedar sin dueño. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllOwnedBy(User owner) {
        fileRepository.findByOwner(owner).forEach(this::delete);
    }

    /**
     * Contenido para descargar. Los públicos los ve cualquiera; los privados, el
     * propietario y la gestión. A quien no puede verlo se le responde como si no
     * existiera, para no revelar qué identificadores son válidos.
     *
     * @param authentication sesión de la petición, o null si no hay
     * @throws NoSuchElementException si no existe o no tiene permiso
     */
    @Transactional(readOnly = true)
    public FileContent open(UUID id, Authentication authentication) {
        StoredFile file = fileRepository.findByUuid(id.toString()).orElseThrow(FileService::notFound);
        boolean isPublic = file.getVisibility() == FileVisibility.PUBLIC;
        if (!isPublic && !canSeePrivate(file, authentication)) {
            throw notFound();
        }
        return new FileContent(
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getSha256(),
                isPublic,
                storage.open(file.getStoragePath()));
    }

    private boolean canSeePrivate(StoredFile file, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        String email = principal.getUsername();
        return file.getOwner().getEmail().equalsIgnoreCase(email)
                || principal.getRole().isManagement()
                || accessRules.stream().anyMatch(rule -> rule.canRead(file, email));
    }

    /** Sin carpetas ni caracteres de control, y con la extensión del formato real. */
    static String safeName(String original, FileFormat format) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}\"]", "")
                .strip();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        if (name.isBlank() || name.startsWith(".")) {
            name = "archivo";
        }
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }
        return name + "." + format.extension();
    }

    private static byte[] read(MultipartFile upload) {
        try {
            return upload.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static void afterTransaction(IntConsumer action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status);
            }
        });
    }

    private static InvalidFieldsException invalid(String message) {
        return new InvalidFieldsException(FIELD, message);
    }

    private static NoSuchElementException notFound() {
        return new NoSuchElementException("El archivo no existe");
    }
}
