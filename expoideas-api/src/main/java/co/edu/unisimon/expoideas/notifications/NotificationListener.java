package co.edu.unisimon.expoideas.notifications;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.common.Masked;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Convierte los eventos de la plataforma en correos. Corre después del commit
 * (si la operación falla, no hay aviso) y en otro hilo (quien pidió la operación
 * no espera al servidor de correo). Un envío que falla se reintenta; si ningún
 * intento sale, queda en el rastro de auditoría y no afecta a la operación, que
 * ya se confirmó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", SPANISH);

    private final MailService mailService;
    private final ExpoideasProperties properties;
    private final MailFailureRecorder failures;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTeamInvitation(TeamInvitationEvent event) {
        String subject = "Te invitaron al proyecto \"" + event.projectTitle() + "\"";
        String body = """
                %s

                %s te invitó a hacer parte del equipo del proyecto "%s" en %s.

                Entra a Idearium y, en Mis proyectos, acepta o rechaza la invitación.%s

                Cátedra UNISIMÓN INNPRENDE · MacondoLab
                """.formatted(
                        greeting(event.inviteeName()),
                        event.leaderName(),
                        event.projectTitle(),
                        event.trackLabel(),
                        appLink("/mis-proyectos"));
        deliver(event.inviteeEmail(), subject, body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountCreated(AccountCreatedEvent event) {
        String subject = "Tu cuenta en Idearium";
        String body = """
                %s

                Te creamos una cuenta en Idearium con el rol de %s.

                Usuario: %s
                Contraseña temporal: %s

                Al entrar por primera vez se te pedirá cambiar la contraseña.%s

                Cátedra UNISIMÓN INNPRENDE · MacondoLab
                """.formatted(
                        greeting(event.fullName()),
                        event.roleLabel(),
                        event.email(),
                        event.temporaryPassword(),
                        appLink("/iniciar-sesion"));
        deliver(event.email(), subject, body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPresentationScheduled(PresentationScheduledEvent event) {
        String subject = (event.rescheduled() ? "Cambió la sustentación de \"" : "Sustentación programada: \"")
                + event.projectTitle() + "\"";
        String body = """
                %s

                Proyecto: %s
                Fecha: %s
                Hora: %s
                Lugar: %s%s%s

                Cátedra UNISIMÓN INNPRENDE · MacondoLab
                """.formatted(
                        event.rescheduled()
                                ? "La sustentación de tu proyecto cambió de fecha, hora o lugar. Estos son los datos nuevos:"
                                : "Ya está programada la sustentación de tu proyecto.",
                        event.projectTitle(),
                        formatDate(event.startsAt()),
                        formatTime(event.startsAt()),
                        event.place(),
                        event.notes() == null || event.notes().isBlank() ? "" : "\nIndicaciones: " + event.notes(),
                        appLink(null));
        for (String recipient : event.recipients()) {
            deliver(recipient, subject, body);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvaluationReminder(EvaluationReminderEvent event) {
        String subject = "Tienes proyectos por calificar";
        String list = event.projectTitles().stream().map(title -> "- " + title).collect(Collectors.joining("\n"));
        String body = """
                %s

                Te falta calificar con la rúbrica:
                %s

                Entra a Idearium y, en Evaluar, abre cada proyecto para calificarlo.%s

                Cátedra UNISIMÓN INNPRENDE · MacondoLab
                """.formatted(greeting(event.fullName()), list, appLink("/jurado/proyectos"));
        deliver(event.email(), subject, body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailVerification(EmailVerificationEvent event) {
        String subject = "Verifica tu correo en Idearium";
        String body = """
                %s

                Para terminar de crear tu cuenta en Idearium, abre este enlace:%s

                El enlace vale 48 horas y sirve una sola vez. Si no creaste la cuenta, ignora este correo: sin abrir el enlace, nadie puede usarla.

                Cátedra UNISIMÓN INNPRENDE · MacondoLab
                """.formatted(greeting(event.fullName()), appLink("/verificar-correo#token=" + event.token()));
        deliver(event.email(), subject, body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordRecovery(PasswordRecoveryEvent event) {
        String subject = "Recupera tu contraseña de Idearium";
        String body =
                """
                %s

                Alguien pidió cambiar la contraseña de tu cuenta en Idearium. Si fuiste tú, abre este enlace para poner una nueva:%s

                El enlace vale una hora y sirve una sola vez. Si no lo pediste, ignora este correo: tu contraseña sigue siendo la misma.

                Cátedra UNISIMÓN INNPRENDE · MacondoLab
                """.formatted(greeting(event.fullName()), appLink("/restablecer-contrasena#token=" + event.token()));
        deliver(event.email(), subject, body);
    }

    /**
     * Envía, y si falla lo vuelve a intentar con una espera cada vez mayor: un
     * SMTP que se cae un momento no debe costar la contraseña temporal de nadie.
     */
    private void deliver(String to, String subject, String body) {
        int attempts = properties.mail().attempts();
        for (int attempt = 1; ; attempt++) {
            try {
                mailService.send(to, subject, body);
                return;
            } catch (RuntimeException e) {
                boolean lastOne = attempt >= attempts || !worthRetrying(e);
                if (lastOne || !pause(properties.mail().retryDelay().multipliedBy(attempt))) {
                    giveUp(to, subject, attempt, e);
                    return;
                }
                log.warn(
                        "No se pudo enviar \"{}\" a {} (intento {} de {}): {}",
                        subject,
                        Masked.email(to),
                        attempt,
                        attempts,
                        e.getMessage());
            }
        }
    }

    /** «Hola, Ana Pérez.», o solo «Hola.» si la cuenta todavía no puso su nombre. */
    private static String greeting(String name) {
        return name == null || name.isBlank() ? "Hola." : "Hola, " + name + ".";
    }

    /** Hay fallos que otro intento no arregla: las credenciales del SMTP o un mensaje mal armado. */
    private static boolean worthRetrying(RuntimeException e) {
        return !(e instanceof MailAuthenticationException
                || e instanceof MailParseException
                || e instanceof MailPreparationException);
    }

    /** @return false si se interrumpió la espera (la API se está apagando): no se sigue intentando */
    private static boolean pause(Duration delay) {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** La operación ya se confirmó: el aviso perdido no sale en la respuesta, pero queda en el log y en el rastro. */
    private void giveUp(String to, String subject, int attempts, RuntimeException cause) {
        // La dirección completa queda en el rastro de auditoría, no en el log.
        log.error(
                "No se pudo enviar \"{}\" a {} tras {} intento(s): {}",
                subject,
                Masked.email(to),
                attempts,
                cause.getMessage());
        try {
            failures.record(to, subject, attempts, cause);
        } catch (RuntimeException e) {
            log.error(
                    "Tampoco se pudo dejar en el rastro el correo perdido a {}: {}", Masked.email(to), e.getMessage());
        }
    }

    /** Un enlace a la plataforma, si se conoce su URL pública (APP_URL). */
    private String appLink(String path) {
        String url = properties.appUrl();
        if (url == null || url.isBlank()) {
            return "";
        }
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return "\n\n" + base + (path == null ? "" : path);
    }

    static String formatDate(LocalDateTime when) {
        String text = DATE.format(when);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String formatTime(LocalDateTime when) {
        // CLDR separa "p. m." con un espacio duro; en un correo de texto plano se lee mejor uno normal.
        return TIME.format(when).toLowerCase(SPANISH).replace(' ', ' ').replace(' ', ' ');
    }
}
