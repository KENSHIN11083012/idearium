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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aprobaciones de cátedra registradas a mano por la gestión (ver SecurityConfig:
 * /api/v1/admin/** es de MacondoLab). Las que nacen de un proyecto las escribe
 * el resultado del proyecto, pero también se listan y se pueden quitar aquí.
 */
@RestController
@RequestMapping("/api/v1/admin/track-approvals")
@RequiredArgsConstructor
public class TrackApprovalController {

    private final TrackApprovalService approvalService;

    /** Las aprobaciones de una persona. */
    @GetMapping
    public List<TrackApprovalResponse> list(@RequestParam Integer userId) {
        return approvalService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrackApprovalResponse create(
            Authentication authentication, @Valid @RequestBody TrackApprovalRequest request) {
        return approvalService.create(authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        approvalService.delete(id);
    }
}
