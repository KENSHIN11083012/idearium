package co.edu.unisimon.expoideas.notifications;

/**
 * Alguien se registró y hay que comprobar que el correo es suyo: se le envía un
 * enlace que solo puede abrir quien lee ese buzón.
 *
 * @param token el del enlace, en claro; viaja una sola vez, en este correo
 */
public record EmailVerificationEvent(String email, String fullName, String token) {}
