package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Reglas comunes al cambio de contraseña propio y al restablecimiento desde la gestión. */
@Component
@RequiredArgsConstructor
class PasswordUpdater {

    private final PasswordEncoder passwordEncoder;

    boolean matches(User user, String password) {
        return passwordEncoder.matches(password, user.getPasswordHash());
    }

    /**
     * Pone una contraseña nueva. La que pone la gestión es temporal (se cambia en
     * el primer ingreso); la que elige la propia persona, no.
     *
     * @throws InvalidFieldsException si la confirmación no coincide o la nueva es igual a la actual
     */
    void replace(User user, String newPassword, String confirmPassword, boolean temporary) {
        if (!newPassword.equals(confirmPassword)) {
            throw new InvalidFieldsException("confirmPassword", "La nueva contraseña y su confirmación no coinciden.");
        }
        if (matches(user, newPassword)) {
            throw new InvalidFieldsException("newPassword", "La nueva contraseña no puede ser igual a la actual.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(temporary);
        // La contraseña anterior pudo estar en otras manos: lo que se abrió con ella se cierra.
        user.closeSessions();
        // Quien se bloqueó probando contraseñas no tiene que esperar, además, a que pase el bloqueo.
        user.clearLoginFailures();
    }

    String hash(String password) {
        return passwordEncoder.encode(password);
    }
}
