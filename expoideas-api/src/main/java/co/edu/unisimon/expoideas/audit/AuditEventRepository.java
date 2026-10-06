package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import java.time.LocalDateTime;
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

    long countByActionAndOccurredAtGreaterThanEqual(Action action, LocalDateTime since);

    Optional<AuditEvent> findFirstByActionOrderByOccurredAtDescIdDesc(Action action);
}
