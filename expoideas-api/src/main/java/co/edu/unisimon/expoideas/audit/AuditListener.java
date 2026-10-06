package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda en el rastro cada {@link AuditableAction} que publican los módulos.
 *
 * <p>Corre en el mismo hilo y en la misma transacción que la acción: quien actúa
 * es la cuenta de la sesión, y si la acción se revierte, su fila se va con
 * ella. Por eso exige una transacción abierta: una acción que se publicara
 * fuera de una quedaría registrada aunque después fallara.
 */
@Component
@RequiredArgsConstructor
class AuditListener {

    private final AuditEventRepository auditRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditableAction action) {
        Optional<String> email = sessionEmail();
        Integer actorId =
                email.flatMap(userRepository::findByEmail).map(User::getId).orElse(null);
        auditRepository.save(AuditEvent.of(action, actorId, email.orElse(null), LocalDateTime.now(clock)));
    }

    /** El correo de quien tiene la sesión; vacío en las rutas públicas (un enlace del correo). */
    private static Optional<String> sessionEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }
}
