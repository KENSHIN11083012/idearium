package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AuditableAction.Target;
import co.edu.unisimon.expoideas.users.PersonalDataSource;
import co.edu.unisimon.expoideas.users.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lo que el rastro guarda sobre la cuenta de una persona: qué se le hizo y
 * cuándo (un cambio de rol, una contraseña restablecida, una suspensión). No
 * dice quién lo hizo: eso es de quien administra, no de ella.
 */
@Component
@RequiredArgsConstructor
class AuditPersonalData implements PersonalDataSource {

    private final AuditEventRepository auditRepository;

    record AccountChange(String action, String detail, LocalDateTime occurredAt) {}

    @Override
    public Map<String, List<?>> dataOf(User user) {
        List<AccountChange> history =
                auditRepository
                        .findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(Target.ACCOUNT, user.getId())
                        .stream()
                        .map(event ->
                                new AccountChange(event.getAction().label(), event.getDetail(), event.getOccurredAt()))
                        .toList();
        return Map.of("accountHistory", history);
    }
}
