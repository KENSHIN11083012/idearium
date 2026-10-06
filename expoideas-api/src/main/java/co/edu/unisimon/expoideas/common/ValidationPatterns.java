package co.edu.unisimon.expoideas.common;

/**
 * Reglas de validación compartidas por las peticiones.
 *
 * <p>El frontend aplica las mismas (lib/validation.js): si cambian aquí, hay que
 * cambiarlas allá.
 */
public final class ValidationPatterns {

    /** 8 a 72 caracteres, con al menos un número y un símbolo. Se aplica con {@link Password}. */
    public static final String PASSWORD = "^(?=.*\\d)(?=.*[\\W_]).{8,72}$";

    /**
     * BCrypt, con el que se guarda la contraseña, solo lee los primeros 72 bytes y
     * rechaza lo que pase de ahí. Se cuenta en bytes: una tilde o una ñ ocupan dos.
     */
    public static final int PASSWORD_MAX_BYTES = 72;

    public static final String PASSWORD_MESSAGE =
            "La contraseña debe tener entre 8 y 72 caracteres (las tildes y la ñ cuentan por dos),"
                    + " incluyendo números y símbolos";

    /** Sin distinguir mayúsculas, igual que el {@code /i} de lib/validation.js en la app. */
    public static final String INSTITUTIONAL_EMAIL = "(?i)^[^@\\s]+@unisimon\\.edu\\.co$";

    public static final String INSTITUTIONAL_EMAIL_MESSAGE = "El correo debe terminar en @unisimon.edu.co";

    private ValidationPatterns() {}
}
