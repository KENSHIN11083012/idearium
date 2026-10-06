package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.LoginSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lleva la cuenta de los intentos fallidos de inicio de sesión de cada cuenta y
 * la bloquea un rato cuando se acumulan: sin esto se pueden probar contraseñas
 * sin límite contra un correo conocido.
 *
 * <p>Cada método corre en su propia transacción. El inicio de sesión que falla
 * termina en una excepción, y el fallo tiene que quedar guardado igual.
 */
@Slf4j
@Component
public class LoginAttempts {

    private final UserRepository userRepository;
    private final Clock clock;
    private final LoginSettings settings;

    public LoginAttempts(UserRepository userRepository, Clock clock, ExpoideasProperties properties) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.settings = properties.login();
    }

    /** Cuánto dura el bloqueo, para decírselo a quien intenta entrar. */
    public Duration lockDuration() {
        return settings.lockDuration();
    }

    /** Si esa cuenta está bloqueada ahora. Un correo sin cuenta nunca lo está. */
    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        return userRepository
                .findByEmail(email)
                .map(user -> user.isLockedAt(now()))
                .orElse(false);
    }

    /** Un fallo más. La fila se bloquea para que dos intentos a la vez cuenten como dos. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        userRepository.findLockedByEmail(email).ifPresent(user -> {
            user.registerFailedLogin(settings.maxFailedAttempts(), now().plus(settings.lockDuration()));
            if (user.isLockedAt(now())) {
                log.warn(
                        "Usuario ID {} bloqueado hasta {} por intentos fallidos de inicio de sesión",
                        user.getId(),
                        user.getLockedUntil());
            }
        });
    }

    /** Entró: los fallos anteriores dejan de contar. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email) {
        userRepository.findByEmail(email).filter(User::hasLoginFailures).ifPresent(User::clearLoginFailures);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
