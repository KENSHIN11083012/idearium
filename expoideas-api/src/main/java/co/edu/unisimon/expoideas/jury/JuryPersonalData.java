package co.edu.unisimon.expoideas.jury;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.users.PersonalDataSource;
import co.edu.unisimon.expoideas.users.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Lo que este módulo guarda de una persona: los proyectos que le asignaron como jurado. */
@Component
@RequiredArgsConstructor
class JuryPersonalData implements PersonalDataSource {

    private final JuryAssignmentRepository assignmentRepository;

    record Assignment(String project, String edition, Track track, LocalDateTime assignedAt) {}

    @Override
    public Map<String, List<?>> dataOf(User user) {
        List<Assignment> assignments = assignmentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(assignment -> new Assignment(
                        assignment.getProject().getTitle(),
                        assignment.getProject().getEdition().getName(),
                        assignment.getProject().getTrack(),
                        assignment.getCreatedAt()))
                .toList();
        return Map.of("juryAssignments", assignments);
    }
}
