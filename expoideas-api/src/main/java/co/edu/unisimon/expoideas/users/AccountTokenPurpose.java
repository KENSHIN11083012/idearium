package co.edu.unisimon.expoideas.users;

import java.time.Duration;

/** Para qué sirve un enlace enviado por correo, y cuánto tiempo vale. */
public enum AccountTokenPurpose {
    /** Demostrar que el correo con el que se registró es suyo. Holgado: puede tardar en abrir el correo. */
    VERIFY_EMAIL(Duration.ofHours(48)),
    /** Poner una contraseña nueva sin conocer la anterior. Corto: da acceso a la cuenta. */
    RESET_PASSWORD(Duration.ofHours(1));

    private final Duration validity;

    AccountTokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}
