package co.edu.unisimon.expoideas.projects;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proyectos inscritos. Todo pide sesión: un proyecto lo ven su equipo, su
 * docente y la gestión. Las invitaciones que uno recibe están en
 * /api/v1/invitations.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectTeamService teamService;

    /** Los proyectos de quien consulta, con las invitaciones que todavía no responde. */
    @GetMapping("/mine")
    public List<ProjectResponse> listMine(Authentication authentication) {
        return projectService.listMine(authentication.getName());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Integer id, Authentication authentication) {
        return projectService.get(id, authentication.getName());
    }

    /** Inscribe un proyecto: quien lo hace queda como líder del equipo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(Authentication authentication, @Valid @RequestBody ProjectRequest request) {
        return projectService.create(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(
            @PathVariable Integer id, Authentication authentication, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, Authentication authentication) {
        projectService.delete(id, authentication.getName());
    }

    /** El resultado del proyecto: lo pone su profesor o la gestión tras el cierre de entregas. */
    @PutMapping("/{id}/result")
    public ProjectResponse setResult(
            @PathVariable Integer id, Authentication authentication, @Valid @RequestBody ProjectResultRequest request) {
        return projectService.setResult(id, authentication.getName(), request);
    }

    /** Invita a un compañero por su correo. */
    @PostMapping("/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse invite(
            @PathVariable Integer id, Authentication authentication, @Valid @RequestBody InvitationRequest request) {
        return teamService.invite(id, authentication.getName(), request);
    }

    /** El líder saca a un integrante o cancela una invitación; cualquiera puede salirse. */
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Integer id, @PathVariable Integer userId, Authentication authentication) {
        teamService.remove(id, userId, authentication.getName());
    }
}
