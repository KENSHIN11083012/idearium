package co.edu.unisimon.expoideas.notifications;

/**
 * Alguien pidió recuperar la contraseña de esta cuenta: se le envía a su correo
 * el enlace para poner una nueva.
 *
 * @param token el del enlace, en claro; viaja una sola vez, en este correo
 */
public record PasswordRecoveryEvent(String email, String fullName, String token) {}
