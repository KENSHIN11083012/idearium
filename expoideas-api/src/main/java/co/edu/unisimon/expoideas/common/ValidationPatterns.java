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

    /**
     * Celular colombiano: diez dígitos y empieza por 3. Se guarda así, sin espacios
     * ni indicativo; la app quita lo que sobra antes de enviarlo.
     */
    public static final String MOBILE_PHONE = "^3\\d{9}$";

    public static final String MOBILE_PHONE_MESSAGE = "Escribe un celular de 10 dígitos, por ejemplo 3001234567";

    /** Semestres que se pueden declarar. Doce cubre los programas más largos. */
    public static final int MIN_SEMESTER = 1;

    public static final int MAX_SEMESTER = 12;

    public static final String SEMESTER_MESSAGE = "Indica el semestre que cursas, del 1 al 12";

    private ValidationPatterns() {}
}
