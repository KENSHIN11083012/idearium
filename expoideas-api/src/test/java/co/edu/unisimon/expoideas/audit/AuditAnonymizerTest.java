package co.edu.unisimon.expoideas.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.AccountAnonymizedEvent;
import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El rastro deja de decir de quién era una cuenta anonimizada, sin perder lo que pasó. */
@ExtendWith(MockitoExtension.class)
class AuditAnonymizerTest {

    private static final String EMAIL = "ana.paz@unisimon.edu.co";
    private static final String NAME = "Ana Paz";
    private static final String ALIAS = "anonimo-3@anonimo.invalid";
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 20, 9, 30);

    @Mock
    private AuditEventRepository auditRepository;

    @InjectMocks
    private AuditAnonymizer anonymizer;

    @Test
    void theEmailGoesAwayWhereverTheRowCopiedIt() {
        // Lo que hizo ella, lo que le hicieron a su cuenta y un correo que no le llegó.
        AuditEvent asActor = event(Action.PROJECT_DELETED, 40, "BioSensor", null, 3, EMAIL);
        AuditEvent asTarget = event(Action.ROLE_CHANGED, 3, EMAIL, "Estudiante → Jurado", 1, "luis@unisimon.edu.co");
        AuditEvent asRecipient =
                event(Action.MAIL_FAILED, null, EMAIL, "Aviso a " + EMAIL + ": buzón lleno", null, null);
        when(auditRepository.findMentioning(EMAIL, NAME)).thenReturn(List.of(asActor, asTarget, asRecipient));

        anonymizer.forget(new AccountAnonymizedEvent(3, EMAIL, NAME, ALIAS));

        assertThat(asActor.getActorEmail()).isEqualTo(ALIAS);
        // Quién fue, por id, y qué pasó siguen ahí.
        assertThat(asActor.getActorId()).isEqualTo(3);
        assertThat(asActor.getTargetLabel()).isEqualTo("BioSensor");
        assertThat(asActor.getOccurredAt()).isEqualTo(WHEN);
        assertThat(asTarget.getTargetLabel()).isEqualTo(ALIAS);
        assertThat(asTarget.getActorEmail()).isEqualTo("luis@unisimon.edu.co");
        assertThat(asTarget.getDetail()).isEqualTo("Estudiante → Jurado");
        assertThat(asRecipient.getTargetLabel()).isEqualTo(ALIAS);
        assertThat(asRecipient.getDetail()).isEqualTo("Aviso a " + ALIAS + ": buzón lleno");
    }

    @Test
    void theNameGoesAwayFromTheDetailButNotFromALongerName() {
        AuditEvent hers = event(Action.JUROR_REMOVED, 40, "BioSensor", "Jurado: Ana Paz", 1, "luis@unisimon.edu.co");
        AuditEvent withGrade =
                event(Action.EVALUATION_EDITED, 7, "BioSensor", "Jurado: Ana Paz · Nota: 4.5 → 3.8", 3, EMAIL);
        // Otra persona, cuyo nombre empieza o termina igual: no es ella.
        AuditEvent longer =
                event(Action.JUROR_REMOVED, 41, "Riego", "Jurado: Ana Paz Rodríguez", 1, "luis@unisimon.edu.co");
        AuditEvent prefixed =
                event(Action.JUROR_REMOVED, 42, "Solar", "Jurado: Mariana Paz", 1, "luis@unisimon.edu.co");
        AuditEvent compound =
                event(Action.JUROR_REMOVED, 43, "Agua", "Jurado: María Ana Paz", 1, "luis@unisimon.edu.co");
        when(auditRepository.findMentioning(EMAIL, NAME))
                .thenReturn(List.of(hers, withGrade, longer, prefixed, compound));

        anonymizer.forget(new AccountAnonymizedEvent(3, EMAIL, NAME, ALIAS));

        assertThat(hers.getDetail()).isEqualTo("Jurado: Cuenta anonimizada");
        assertThat(withGrade.getDetail()).isEqualTo("Jurado: Cuenta anonimizada · Nota: 4.5 → 3.8");
        assertThat(longer.getDetail()).isEqualTo("Jurado: Ana Paz Rodríguez");
        assertThat(prefixed.getDetail()).isEqualTo("Jurado: Mariana Paz");
        assertThat(compound.getDetail()).isEqualTo("Jurado: María Ana Paz");
    }

    @Test
    void anAccountWithoutNameOnlyLosesItsEmail() {
        AuditEvent row = event(Action.PASSWORD_RESET, 3, EMAIL, null, 1, "luis@unisimon.edu.co");
        when(auditRepository.findMentioning(EMAIL, null)).thenReturn(List.of(row));

        anonymizer.forget(new AccountAnonymizedEvent(3, EMAIL, null, ALIAS));

        assertThat(row.getTargetLabel()).isEqualTo(ALIAS);
        assertThat(row.getDetail()).isNull();
    }

    private static AuditEvent event(
            Action action, Integer targetId, String label, String detail, Integer actorId, String actorEmail) {
        return AuditEvent.of(new AuditableAction(action, targetId, label, detail), actorId, actorEmail, WHEN);
    }
}
