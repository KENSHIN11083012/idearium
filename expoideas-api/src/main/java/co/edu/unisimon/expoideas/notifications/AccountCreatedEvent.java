package co.edu.unisimon.expoideas.notifications;

/**
 * La gestión creó una cuenta con contraseña temporal (un jurado externo, un
 * profesor). El correo es la forma en que esa persona se entera de que puede
 * entrar; se le pedirá cambiar la contraseña en el primer ingreso.
 *
 * @param temporaryPassword la que puso la gestión; viaja una sola vez, en este correo
 * @param roleLabel         "Jurado", "Profesor"...
 */
public record AccountCreatedEvent(String email, String fullName, String temporaryPassword, String roleLabel) {}
