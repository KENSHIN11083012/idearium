package co.edu.unisimon.expoideas.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.AuditableAction.Target;
import co.edu.unisimon.expoideas.common.TimeConfig;
import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditListenerTest {

    private static final String EMAIL = "coordinacion@unisimon.edu.co";

    @Mock
    private AuditEventRepository auditRepository;

    @Mock
    private UserRepository userRepository;

    private AuditListener listener;

    @BeforeEach
    void setUp() {
        // Las 02:30 UTC del 21 son las 21:30 del 20 en Colombia.
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:30:00Z"), TimeConfig.ZONE);
        listener = new AuditListener(auditRepository, userRepository, clock);
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theActorIsTheAccountWithTheSessionAndTheTimeIsColombias() {
        signIn();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(TestData.user(3, EMAIL, Role.MACONDOLAB)));

        listener.record(new AuditableAction(Action.ROLE_CHANGED, 12, "ana@unisimon.edu.co", "Estudiante → Profesor"));

        AuditEvent saved = saved();
        assertThat(saved.getActorId()).isEqualTo(3);
        assertThat(saved.getActorEmail()).isEqualTo(EMAIL);
        assertThat(saved.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 21, 30));
        assertThat(saved.getAction()).isEqualTo(Action.ROLE_CHANGED);
        assertThat(saved.getTargetType()).isEqualTo(Target.ACCOUNT);
        assertThat(saved.getTargetId()).isEqualTo(12);
        assertThat(saved.getTargetLabel()).isEqualTo("ana@unisimon.edu.co");
        assertThat(saved.getDetail()).isEqualTo("Estudiante → Profesor");
    }

    @Test
    void withoutASessionTheActionIsRecordedWithoutAnActor() {
        listener.record(new AuditableAction(Action.ROLE_CHANGED, 12, "ana@unisimon.edu.co", null));

        assertThat(saved().getActorId()).isNull();
        assertThat(saved().getActorEmail()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void anAnonymousRequestIsNotAnActor() {
        // Así llega quien abre un enlace del correo sin sesión.
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "clave", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        listener.record(new AuditableAction(Action.ROLE_CHANGED, 12, "ana@unisimon.edu.co", null));

        assertThat(saved().getActorEmail()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void aTextThatDoesNotFitIsCutInsteadOfFailingTheAction() {
        signIn();

        listener.record(new AuditableAction(Action.PROJECT_DELETED, 1, "t".repeat(300), "d".repeat(900)));

        assertThat(saved().getTargetLabel()).hasSize(200).endsWith("t…");
        assertThat(saved().getDetail()).hasSize(500).endsWith("d…");
    }

    private static void signIn() {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        EMAIL, null, List.of(new SimpleGrantedAuthority("ROLE_MACONDOLAB"))));
    }

    private AuditEvent saved() {
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepository).save(event.capture());
        return event.getValue();
    }
}
