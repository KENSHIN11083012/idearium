package co.edu.unisimon.expoideas.files;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** Descarga de archivos por su identificador público. */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * Públicos: sin sesión y con caché de un año, porque un identificador nunca
     * cambia de contenido. Privados: con sesión y permiso, sin caché; si no, 404.
     */
    @GetMapping("/api/v1/files/{id}")
    @SecurityRequirements
    public ResponseEntity<Resource> download(@PathVariable UUID id, Authentication authentication, WebRequest request) {
        FileContent file = fileService.open(id, authentication);

        if (request.checkNotModified(file.sha256())) {
            return null; // 304: el navegador ya lo tiene.
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .eTag(file.sha256())
                .cacheControl(
                        file.isPublic()
                                ? CacheControl.maxAge(Duration.ofDays(365))
                                        .cachePublic()
                                        .immutable()
                                : CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                // Si alguien lograra colar HTML o scripts, el navegador no los ejecuta en nuestro origen.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(file.resource());
    }
}
