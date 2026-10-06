package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.AuditableAction.Target;
import java.time.LocalDateTime;

/**
 * Una fila del rastro.
 *
 * @param actorId     null si la cuenta que actuó ya no existe o nadie tenía sesión
 * @param actionLabel la acción, con el nombre que ve la gestión
 */
public record AuditEventResponse(
        Long id,
        LocalDateTime occurredAt,
        Integer actorId,
        String actorEmail,
        Action action,
        String actionLabel,
        Target targetType,
        Integer targetId,
        String targetLabel,
        String detail) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getActorId(),
                event.getActorEmail(),
                event.getAction(),
                event.getAction().label(),
                event.getTargetType(),
                event.getTargetId(),
                event.getTargetLabel(),
                event.getDetail());
    }
}
