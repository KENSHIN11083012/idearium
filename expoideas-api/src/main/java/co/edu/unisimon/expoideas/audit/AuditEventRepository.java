package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.AuditableAction.Target;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** El rastro que cumple los filtros. Un filtro en null no filtra; {@code until} no entra. */
    @Query("""
            select e from AuditEvent e
            where (:action is null or e.action = :action)
              and (:since is null or e.occurredAt >= :since)
              and (:until is null or e.occurredAt < :until)
            """)
    Page<AuditEvent> search(
            @Param("action") Action action,
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until,
            Pageable pageable);

    /**
     * Las filas que guardan el correo de esa persona (como autora o como aquello
     * sobre lo que se actuó) o que la mencionan en el detalle.
     *
     * @param name su nombre completo, o null si no lo tenía: entonces el detalle solo se busca por el correo
     */
    @Query("""
            select e from AuditEvent e
            where e.actorEmail = :email
               or e.targetLabel = :email
               or e.detail like concat('%', :email, '%')
               or (:name is not null and e.detail like concat('%', :name, '%'))
            """)
    List<AuditEvent> findMentioning(@Param("email") String email, @Param("name") String name);

    /** Lo que se hizo sobre esa cuenta, en orden. */
    List<AuditEvent> findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(Target targetType, Integer targetId);

    long countByActionAndOccurredAtGreaterThanEqual(Action action, LocalDateTime since);

    Optional<AuditEvent> findFirstByActionOrderByOccurredAtDescIdDesc(Action action);
}
