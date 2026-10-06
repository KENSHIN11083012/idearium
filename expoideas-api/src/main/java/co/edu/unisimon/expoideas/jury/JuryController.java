package co.edu.unisimon.expoideas.jury;

import co.edu.unisimon.expoideas.projects.ProjectResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jurados. La gestión asigna y quita (SecurityConfig); cualquier sesión consulta
 * los proyectos que tiene por evaluar.
 */
@RestController
@RequiredArgsConstructor
public class JuryController {

    private final JuryService juryService;

    @GetMapping("/api/v1/projects/{projectId}/jurors")
    public List<JurorResponse> list(@PathVariable Integer projectId) {
        return juryService.list(projectId);
    }

    @PostMapping("/api/v1/projects/{projectId}/jurors")
    @ResponseStatus(HttpStatus.CREATED)
    public JurorResponse assign(
            @PathVariable Integer projectId, Authentication authentication, @Valid @RequestBody JurorRequest request) {
        return juryService.assign(projectId, authentication.getName(), request);
    }

    @DeleteMapping("/api/v1/projects/{projectId}/jurors/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Integer projectId, @PathVariable Integer userId) {
        juryService.remove(projectId, userId);
    }

    /** Los proyectos que quien consulta tiene asignados como jurado. */
    @GetMapping("/api/v1/jury/projects")
    public List<ProjectResponse> myProjects(Authentication authentication) {
        return juryService.myProjects(authentication.getName());
    }
}
