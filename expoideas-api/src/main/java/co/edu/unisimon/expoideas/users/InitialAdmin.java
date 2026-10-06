package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.AdminSettings;
import co.edu.unisimon.expoideas.common.ValidationPatterns;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * El primer administrador de una instalación nueva, sin tocar la base de datos a
 * mano: sale de ADMIN_EMAIL y ADMIN_PASSWORD al arrancar.
 *
 * <p>Solo actúa mientras la plataforma no tenga ningún administrador. En cuanto
 * hay uno, las dos variables se ignoran: no sirven para recuperar el acceso ni
 * para nombrar a otro, que es cosa de quien ya administra. Por eso se pueden
 * quitar del entorno después del primer arranque.
 *
 * <p>La contraseña es temporal: el primer ingreso obliga a cambiarla, a
 * autorizar el tratamiento de datos y a poner el nombre, como a cualquier cuenta
 * creada por la gestión.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InitialAdmin implements ApplicationRunner {

    private final ExpoideasProperties properties;
    private final UserRepository userRepository;
    private final PasswordUpdater passwords;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AdminSettings admin = properties.admin();
        if (admin.email().isBlank() || userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        String email = admin.email().strip();

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            // Ya se registró por su cuenta: conserva su contraseña y lo que haya completado.
            User user = existing.get();
            Role previous = user.getRole();
            user.setRole(Role.ADMIN);
            user.setPendingRole(null);
            events.publishEvent(new AuditableAction(
                    Action.ADMIN_BOOTSTRAPPED,
                    user.getId(),
                    email,
                    previous.label() + " → " + Role.ADMIN.label() + " (ADMIN_EMAIL, al arrancar)"));
            log.info("Usuario ID {} queda como primer administrador (ADMIN_EMAIL)", user.getId());
            return;
        }

        if (!isUsable(admin.password())) {
            log.error(
                    "No hay ningún administrador y ADMIN_EMAIL está definido, pero ADMIN_PASSWORD falta o no cumple"
                            + " los requisitos ({}). No se crea la cuenta.",
                    ValidationPatterns.PASSWORD_MESSAGE);
            return;
        }
        User created = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwords.hash(admin.password()))
                .role(Role.ADMIN)
                .mustChangePassword(true)
                .build());
        events.publishEvent(new AuditableAction(
                Action.ADMIN_BOOTSTRAPPED, created.getId(), email, "Cuenta creada (ADMIN_EMAIL, al arrancar)"));
        log.info("Usuario ID {} creado como primer administrador (ADMIN_EMAIL)", created.getId());
    }

    /** Las mismas reglas que cualquier contraseña: no se abre la plataforma con una débil. */
    private static boolean isUsable(String password) {
        return password != null
                && password.matches(ValidationPatterns.PASSWORD)
                && password.getBytes(StandardCharsets.UTF_8).length <= ValidationPatterns.PASSWORD_MAX_BYTES;
    }
}
