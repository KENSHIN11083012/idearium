package co.edu.unisimon.expoideas.presentations;

import co.edu.unisimon.expoideas.editions.Track;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sustentaciones. La gestión programa, cambia y quita (SecurityConfig); el
 * equipo y el profesor del grupo consultan la de su proyecto.
 */
@RestController
@RequiredArgsConstructor
public class PresentationController {

    private final PresentationService presentationService;

    /** La cita del proyecto, o 204 si todavía no está programada. */
    @GetMapping("/api/v1/projects/{projectId}/presentation")
    public ResponseEntity<PresentationResponse> get(@PathVariable Integer projectId, Authentication authentication) {
        return presentationService
                .get(projectId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Programa o cambia la cita; avisa por correo al equipo y al profesor. */
    @PutMapping("/api/v1/projects/{projectId}/presentation")
    public PresentationResponse schedule(
            @PathVariable Integer projectId, @Valid @RequestBody PresentationRequest request) {
        return presentationService.schedule(projectId, request);
    }

    @DeleteMapping("/api/v1/projects/{projectId}/presentation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Integer projectId) {
        presentationService.cancel(projectId);
    }

    /** La agenda de una cátedra en una edición, en orden de fecha. */
    @GetMapping("/api/v1/presentations")
    public List<PresentationAgendaResponse> agenda(@RequestParam Integer editionId, @RequestParam Track track) {
        return presentationService.agenda(editionId, track);
    }
}
