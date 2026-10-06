package co.edu.unisimon.expoideas.jury;

import co.edu.unisimon.expoideas.users.Role;
import java.time.LocalDateTime;

/** Un jurado asignado a un proyecto, como lo ve la gestión. */
public record JurorResponse(
        Integer id, Integer userId, String fullName, String email, Role role, LocalDateTime assignedAt) {

    public static JurorResponse from(JuryAssignment assignment) {
        return new JurorResponse(
                assignment.getId(),
                assignment.getUser().getId(),
                assignment.getUser().fullName(),
                assignment.getUser().getEmail(),
                assignment.getUser().getRole(),
                assignment.getCreatedAt());
    }
}
