package co.edu.unisimon.expoideas.projects;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Integer> {

    @EntityGraph(attributePaths = {"project", "project.edition", "project.members", "project.members.user", "user"})
    Optional<ProjectMember> findWithProjectById(Integer id);

    /** Quita el celular de todas las inscripciones de esa cuenta; devuelve cuántas lo tenían. */
    @Modifying
    @Query("update ProjectMember m set m.phone = null where m.user.id = :userId and m.phone is not null")
    int forgetPhoneOf(@Param("userId") Integer userId);

    /** Invitaciones sin responder de una cuenta, de la más reciente a la más antigua. */
    @EntityGraph(attributePaths = {"project", "project.edition", "project.members", "project.members.user"})
    List<ProjectMember> findByUserIdAndStatusOrderByInvitedAtDesc(Integer userId, MembershipStatus status);
}
