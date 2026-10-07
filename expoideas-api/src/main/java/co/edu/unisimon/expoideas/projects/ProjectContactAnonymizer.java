package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.common.AccountAnonymizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Al anonimizar una cuenta, su paso por los equipos se conserva (es historia de
 * los proyectos), pero el celular que dio como líder deja de estar: es lo único
 * de esa fila que dice de quién era. Va en la misma transacción que la
 * anonimización, o no va.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ProjectContactAnonymizer {

    private final ProjectMemberRepository memberRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(AccountAnonymizedEvent event) {
        int rows = memberRepository.forgetPhoneOf(event.userId());
        log.info("Cuenta ID {} anonimizada: {} inscripciones dejan de tener su celular", event.userId(), rows);
    }
}
