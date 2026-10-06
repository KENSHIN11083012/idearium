package co.edu.unisimon.expoideas.notifications;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja en el rastro de auditoría un correo que no se pudo enviar después de
 * todos los intentos, para que no quede solo en el log: si era la contraseña
 * temporal de una cuenta, alguien tiene que saber que no llegó.
 *
 * <p>Se guarda a quién iba y el asunto, nunca el cuerpo: puede llevar una
 * contraseña temporal o un enlace de un solo uso.
 */
@Component
@RequiredArgsConstructor
public class MailFailureRecorder {

    private final ApplicationEventPublisher events;

    /**
     * Abre su propia transacción: los correos salen después del commit de la
     * operación que los pidió, y lo que se escribiera en esa ya no se guardaría.
     *
     * @param attempts cuántas veces se intentó
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String to, String subject, int attempts, RuntimeException cause) {
        events.publishEvent(new AuditableAction(
                Action.MAIL_FAILED, null, to, "\"" + subject + "\" · intentos: " + attempts + " · " + reason(cause)));
    }

    /** El motivo de fondo en una línea: el del servidor de correo, no el del envoltorio de Spring. */
    private static String reason(RuntimeException cause) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(cause);
        String message = root.getMessage();
        String name = root.getClass().getSimpleName();
        return message == null || message.isBlank()
                ? name
                : name + ": " + message.lines().findFirst().orElse("");
    }
}
