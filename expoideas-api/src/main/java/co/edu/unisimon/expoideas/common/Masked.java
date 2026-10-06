package co.edu.unisimon.expoideas.common;

/**
 * Datos personales como se escriben en el log: lo justo para seguir un problema,
 * sin dejar el dato entero en un archivo que rota, se copia y lo lee quien opera
 * el servidor. El dato completo, cuando hace falta, está en la base (el rastro
 * de auditoría), donde sí hay control de acceso.
 */
public final class Masked {

    private Masked() {}

    /** {@code ana.perez@unisimon.edu.co} → {@code a***@unisimon.edu.co}. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "(sin correo)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
