package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.catalogs.AcademicProgram;
import co.edu.unisimon.expoideas.catalogs.Campus;
import co.edu.unisimon.expoideas.catalogs.Faculty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos de una cuenta para la propia persona (/users/me) y para la gestión. No
 * incluye el hash de la contraseña ni es apto para un listado público (lleva el
 * correo).
 *
 * <p>La adscripción viaja con nombre (para mostrar) e id (para los formularios).
 * {@code photoId} es el identificador público de la foto (GET /api/v1/files/{id}).
 * {@code suspended} dice si la gestión le cortó el acceso, y {@code pendingRole},
 * el rol del listado de la cátedra que la gestión aún no ha confirmado.
 */
public record UserResponse(
        Integer id,
        String firstName,
        String lastName,
        String email,
        Role role,
        String photoId,
        LocalDateTime createdAt,
        Integer campusId,
        String campus,
        Integer facultyId,
        String faculty,
        Integer academicProgramId,
        String academicProgram,
        List<OnboardingStep> pendingSteps,
        boolean suspended,
        Role pendingRole) {

    /** Requiere las relaciones cargadas (grafo {@link User#WITH_PROFILE} o dentro de la transacción). */
    public static UserResponse from(User user) {
        Campus campus = user.getCampus();
        Faculty faculty = user.getFaculty();
        AcademicProgram program = user.getAcademicProgram();
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getPhoto() != null ? user.getPhoto().getUuid() : null,
                user.getCreatedAt(),
                campus != null ? campus.getId() : null,
                campus != null ? campus.getName() : null,
                faculty != null ? faculty.getId() : null,
                faculty != null ? faculty.getName() : null,
                program != null ? program.getId() : null,
                program != null ? program.getName() : null,
                user.pendingSteps(),
                !user.isEnabled(),
                user.getPendingRole());
    }
}
