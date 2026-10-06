package co.edu.unisimon.expoideas.projects;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Integer> {

    @EntityGraph(attributePaths = {"project", "project.edition", "project.members", "project.members.user", "user"})
    Optional<ProjectMember> findWithProjectById(Integer id);

    /** Invitaciones sin responder de una cuenta, de la más reciente a la más antigua. */
    @EntityGraph(attributePaths = {"project", "project.edition", "project.members", "project.members.user"})
    List<ProjectMember> findByUserIdAndStatusOrderByInvitedAtDesc(Integer userId, MembershipStatus status);
}
