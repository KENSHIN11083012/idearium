package co.edu.unisimon.expoideas.status;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El estado de la instalación: versión, disco de los archivos y correo. Solo lo
 * lee un administrador (lo exige SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/status")
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;

    @GetMapping
    public StatusResponse status() {
        return statusService.status();
    }
}
