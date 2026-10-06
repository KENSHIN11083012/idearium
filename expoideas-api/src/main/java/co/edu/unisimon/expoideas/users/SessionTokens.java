package co.edu.unisimon.expoideas.users;

/**
 * Emite el token de sesión de una cuenta. Lo implementa el módulo de seguridad:
 * así las cuentas pueden devolver un token nuevo (al cambiar la contraseña, que
 * cierra los anteriores) sin saber cómo se firma.
 */
public interface SessionTokens {

    /** Un token que vale desde ahora, con la versión de sesión que la cuenta tiene en este momento. */
    String issueFor(User user);
}
