package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.common.Masked;
import co.edu.unisimon.expoideas.users.User;

/**
 * Un estudiante que el líder puede invitar, tal como sale al buscarlo por su
 * nombre. Lleva lo justo para reconocerlo entre tocayos: de dónde es y su
 * correo enmascarado. El correo completo no sale: quien busca todavía no es su
 * compañero.
 */
public record InvitationCandidateResponse(
        Integer id, String fullName, String email, String campus, String faculty, String academicProgram) {

    public static InvitationCandidateResponse from(User user) {
        return new InvitationCandidateResponse(
                user.getId(),
                user.fullName(),
                Masked.email(user.getEmail()),
                user.getCampus() != null ? user.getCampus().getName() : null,
                user.getFaculty() != null ? user.getFaculty().getName() : null,
                user.getAcademicProgram() != null ? user.getAcademicProgram().getName() : null);
    }
}
