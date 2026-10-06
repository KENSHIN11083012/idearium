package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.AuditableAction.Target;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una fila del rastro: quién hizo qué, sobre qué y cuándo. No tiene setters: lo
 * que se registró no se corrige.
 *
 * <p>La cuenta de quien actuó va como id suelto y no como relación: este módulo
 * no necesita nada más de ella, y el correo queda copiado para que la fila siga
 * diciendo quién fue cuando la cuenta ya no exista.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    static final int LABEL_LENGTH = 200;
    static final int DETAIL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_id")
    private Integer actorId;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private Target targetType;

    @Column(name = "target_id")
    private Integer targetId;

    @Column(name = "target_label", length = LABEL_LENGTH)
    private String targetLabel;

    @Column(length = DETAIL_LENGTH)
    private String detail;

    /**
     * @param actorId    la cuenta con la sesión; null si nadie la tenía
     * @param actorEmail su correo, o null
     */
    static AuditEvent of(AuditableAction action, Integer actorId, String actorEmail, LocalDateTime now) {
        AuditEvent event = new AuditEvent();
        event.occurredAt = now;
        event.actorId = actorId;
        event.actorEmail = actorEmail;
        event.action = action.action();
        event.targetType = action.action().target();
        event.targetId = action.targetId();
        // Un texto largo no debe tumbar la acción que se está registrando: se recorta.
        event.targetLabel = fit(action.targetLabel(), LABEL_LENGTH);
        event.detail = fit(action.detail(), DETAIL_LENGTH);
        return event;
    }

    private static String fit(String text, int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length - 1) + "…";
    }
}
