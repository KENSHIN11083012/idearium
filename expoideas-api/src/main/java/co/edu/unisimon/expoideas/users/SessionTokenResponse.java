package co.edu.unisimon.expoideas.users;

/**
 * El token con el que sigue una sesión después de un cambio que cerró las
 * anteriores (hoy, cambiar la propia contraseña).
 */
public record SessionTokenResponse(String token) {}
