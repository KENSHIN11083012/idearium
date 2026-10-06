package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.notifications.EmailVerificationEvent;
import co.edu.unisimon.expoideas.notifications.MailService;
import co.edu.unisimon.expoideas.notifications.PasswordRecoveryEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que una cuenta resuelve con un enlace enviado a su correo: verificar que el
 * correo es suyo y poner una contraseña nueva cuando olvidó la anterior.
 *
 * <p>Todo depende de que la plataforma pueda enviar correos. Sin servidor de
 * correo no se pide verificación al registrarse (el rol del listado lo confirma
 * la gestión) y la contraseña la restablece la gestión desde Usuarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLinkService {

    /** Entre un enlace y el siguiente del mismo tipo: nadie llena un buzón ajeno a punta de clics. */
    private static final Duration RESEND_WAIT = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final AccountTokens tokens;
    private final PasswordUpdater passwords;
    private final MailService mail;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Si se puede escribir a las cuentas con un enlace a la plataforma. */
    public boolean canSendLinks() {
        return mail.canSendLinks();
    }

    /** Le envía a una cuenta recién registrada el enlace para verificar su correo. */
    @Transactional
    public void sendVerification(User user) {
        String token = tokens.issue(user, AccountTokenPurpose.VERIFY_EMAIL);
        // El correo sale después del commit; si el registro no se guarda, no hay enlace.
        events.publishEvent(new EmailVerificationEvent(user.getEmail(), user.nameForGreeting(), token));
    }

    /**
     * Vuelve a enviar el enlace a quien tiene la sesión abierta y aún no verifica.
     *
     * @throws ConflictException si ya está verificada o se le acaba de enviar uno
     */
    @Transactional
    public void resendVerification(String email) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No existe una cuenta con el correo: " + email));
        if (!user.isEmailVerificationPending()) {
            throw new ConflictException("Tu correo ya está verificado.");
        }
        if (tokens.issuedWithin(user, AccountTokenPurpose.VERIFY_EMAIL, RESEND_WAIT)) {
            throw new ConflictException("Acabamos de enviarte un enlace. Revisa tu correo o espera un minuto.");
        }
        sendVerification(user);
    }

    /**
     * Da por verificado el correo de la cuenta dueña del enlace. Si el listado de
     * la cátedra le reservaba un rol, lo recibe aquí.
     *
     * @throws ConflictException si el enlace no existe, ya se usó o venció
     */
    @Transactional
    public void verifyEmail(String token) {
        User user = tokens.consume(token, AccountTokenPurpose.VERIFY_EMAIL);
        verify(user);
        log.info("Usuario ID {} verificó su correo", user.getId());
    }

    /**
     * Envía el enlace para poner una contraseña nueva, si ese correo tiene una
     * cuenta activa. No dice si la tiene: quien pregunta no debe poder averiguar
     * qué correos están registrados.
     *
     * @throws ConflictException si la plataforma no puede enviar correos
     */
    @Transactional
    public void requestPasswordRecovery(String email) {
        if (!canSendLinks()) {
            throw new ConflictException("La recuperación por correo todavía no está disponible."
                    + " Pide a la coordinación de la cátedra que restablezca tu contraseña.");
        }
        userRepository
                .findByEmail(email.strip())
                .filter(User::isEnabled)
                .filter(user -> !tokens.issuedWithin(user, AccountTokenPurpose.RESET_PASSWORD, RESEND_WAIT))
                .ifPresent(user -> {
                    String token = tokens.issue(user, AccountTokenPurpose.RESET_PASSWORD);
                    events.publishEvent(new PasswordRecoveryEvent(user.getEmail(), user.nameForGreeting(), token));
                    log.info("Usuario ID {} pidió recuperar su contraseña", user.getId());
                });
    }

    /**
     * Pone la contraseña nueva de la cuenta dueña del enlace. Cierra las sesiones
     * abiertas con la anterior y levanta el bloqueo por intentos fallidos. Abrir
     * el enlace también demuestra que el correo es suyo.
     *
     * @throws ConflictException      si el enlace no existe, ya se usó o venció, o la cuenta está suspendida
     * @throws InvalidFieldsException si la confirmación no coincide o la nueva es igual a la actual
     */
    @Transactional
    public void resetPassword(PasswordRecoveryResetRequest request) {
        User user = tokens.consume(request.token(), AccountTokenPurpose.RESET_PASSWORD);
        if (!user.isEnabled()) {
            throw new ConflictException("Tu cuenta está suspendida. Comunícate con la coordinación de la cátedra.");
        }
        passwords.replace(user, request.newPassword(), request.confirmPassword(), false);
        verify(user);
        log.info("Usuario ID {} puso una contraseña nueva con un enlace de recuperación", user.getId());
    }

    /**
     * Da el correo por verificado. Con él llega el rol que el listado de la
     * cátedra hubiera dejado pendiente: como nadie de la gestión interviene,
     * queda en el rastro de dónde salió.
     */
    private void verify(User user) {
        Role previous = user.getRole();
        user.verifyEmail(now());
        if (user.getRole() != previous) {
            events.publishEvent(new AuditableAction(
                    Action.ROLE_CHANGED,
                    user.getId(),
                    user.getEmail(),
                    previous.label() + " → " + user.getRole().label() + " (del listado, al verificar el correo)"));
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
