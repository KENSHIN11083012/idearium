package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.files.FileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Los archivos de un proyecto. Se listan para quien puede ver el proyecto y los
 * sube o quita su equipo, hasta el cierre de entregas de la edición.
 *
 * <p>El contenido se descarga por el módulo de archivos: GET /api/v1/files/{fileId}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/deliverables")
@RequiredArgsConstructor
public class DeliverableController {

    private final DeliverableService deliverableService;

    @GetMapping
    public List<DeliverableGroupResponse> list(@PathVariable Integer projectId, Authentication authentication) {
        return deliverableService.list(projectId, authentication.getName());
    }

    /** Sube un archivo (máximo 5 MB) para uno de los entregables de la cátedra. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DeliverableGroupResponse> upload(
            @PathVariable Integer projectId,
            @RequestParam Integer deliverableTypeId,
            @RequestPart(FileService.FIELD) MultipartFile file,
            Authentication authentication) {
        return deliverableService.upload(projectId, deliverableTypeId, file, authentication.getName());
    }

    /** Registra un enlace para un entregable de tipo LINK (video, prototipo en línea). */
    @PostMapping("/links")
    public List<DeliverableGroupResponse> submitLink(
            @PathVariable Integer projectId,
            @Valid @RequestBody DeliverableLinkRequest request,
            Authentication authentication) {
        return deliverableService.submitLink(projectId, request, authentication.getName());
    }

    @DeleteMapping("/{deliverableId}")
    public List<DeliverableGroupResponse> delete(
            @PathVariable Integer projectId, @PathVariable Integer deliverableId, Authentication authentication) {
        return deliverableService.delete(projectId, deliverableId, authentication.getName());
    }
}
