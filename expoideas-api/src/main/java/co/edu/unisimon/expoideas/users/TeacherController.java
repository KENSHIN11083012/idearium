package co.edu.unisimon.expoideas.users;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Docentes de la plataforma, para que el líder elija el del grupo al inscribir
 * su proyecto. Pide sesión y solo muestra el nombre y la facultad: el listado
 * con correos y adscripción completa es de la gestión (/api/v1/admin/users).
 */
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final UserRepository userRepository;

    @GetMapping
    public List<TeacherResponse> list() {
        return userRepository.findAllWithProfileByRole(Role.TEACHER).stream()
                .sorted(Comparator.comparing(User::fullName, String.CASE_INSENSITIVE_ORDER))
                .map(TeacherResponse::from)
                .toList();
    }
}
