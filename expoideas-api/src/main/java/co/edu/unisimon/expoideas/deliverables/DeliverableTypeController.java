package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.files.FileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lo que pide cada cátedra. Lo escribe MacondoLab (ver SecurityConfig); leerlo
 * pide sesión, porque lo consulta el equipo antes de subir sus archivos.
 */
@RestController
@RequestMapping("/api/v1/deliverable-types")
@RequiredArgsConstructor
public class DeliverableTypeController {

    private final DeliverableTypeService deliverableTypeService;

    @GetMapping
    public List<DeliverableTypeResponse> list(@RequestParam Integer editionId, @RequestParam Track track) {
        return deliverableTypeService.list(editionId, track);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliverableTypeResponse create(@Valid @RequestBody DeliverableTypeRequest request) {
        return deliverableTypeService.create(request);
    }

    @PutMapping("/{id}")
    public DeliverableTypeResponse update(
            @PathVariable Integer id, @Valid @RequestBody DeliverableTypeRequest request) {
        return deliverableTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        deliverableTypeService.delete(id);
    }

    /** Sube o reemplaza la plantilla del entregable: PDF, DOCX o PPTX de hasta 5 MB. */
    @PutMapping(path = "/{id}/template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DeliverableTypeResponse uploadTemplate(
            @PathVariable Integer id,
            @RequestPart(FileService.FIELD) MultipartFile file,
            Authentication authentication) {
        return deliverableTypeService.uploadTemplate(id, file, authentication.getName());
    }

    @DeleteMapping("/{id}/template")
    public DeliverableTypeResponse deleteTemplate(@PathVariable Integer id) {
        return deliverableTypeService.deleteTemplate(id);
    }
}
