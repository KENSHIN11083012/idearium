package co.edu.unisimon.expoideas.projects;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Las invitaciones que uno recibió para entrar al equipo de un proyecto. */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final ProjectTeamService teamService;

    @GetMapping
    public List<InvitationResponse> listMine(Authentication authentication) {
        return teamService.listMine(authentication.getName());
    }

    /** Aceptar devuelve el proyecto completo: quien acepta ya es del equipo. */
    @PostMapping("/{id}/acceptance")
    public ProjectResponse accept(@PathVariable Integer id, Authentication authentication) {
        return teamService.accept(id, authentication.getName());
    }

    @PostMapping("/{id}/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Integer id, Authentication authentication) {
        teamService.decline(id, authentication.getName());
    }
}
