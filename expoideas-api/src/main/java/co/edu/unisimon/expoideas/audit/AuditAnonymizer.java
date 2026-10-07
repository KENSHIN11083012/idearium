package co.edu.unisimon.expoideas.audit;

import co.edu.unisimon.expoideas.common.AccountAnonymizedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuando una cuenta se anonimiza, el rastro deja de decir de quién era: su
 * correo, que cada fila copia para sobrevivir a la cuenta, pasa a ser el correo
 * sin dueño, y su nombre, donde quedó escrito en un detalle, se reemplaza. El
 * id de la cuenta se conserva, así que sigue sabiéndose qué hizo «esa cuenta».
 *
 * <p>Corre en la transacción de la anonimización: o se quitan las dos cosas o
 * ninguna. Del detalle solo se quita el nombre completo tal como estaba
 * guardado; si alguien lo escribió de otra forma, ahí se queda.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AuditAnonymizer {

    /** Lo que queda donde un detalle decía el nombre. */
    static final String NAME_PLACEHOLDER = "Cuenta anonimizada";

    private final AuditEventRepository auditRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(AccountAnonymizedEvent event) {
        List<AuditEvent> mentions = auditRepository.findMentioning(event.formerEmail(), event.formerName());
        mentions.forEach(row -> row.forget(event.formerEmail(), event.formerName(), event.alias(), NAME_PLACEHOLDER));
        log.info("Cuenta ID {} anonimizada: {} filas del rastro dejan de nombrarla", event.userId(), mentions.size());
    }
}
