package co.edu.unisimon.expoideas.users;

/**
 * Rol de una cuenta. Se guarda como texto en {@code users.role} con un CHECK:
 * agregar un rol es añadirlo aquí y actualizar la constraint con una migración.
 *
 * <p>En Spring Security cada rol es {@code ROLE_} + su nombre, y SecurityConfig
 * declara que ROLE_ADMIN incluye a ROLE_MACONDOLAB.
 */
public enum Role {
    /** Parte técnica de la plataforma. Puede todo lo de MacondoLab y además gestionar cuentas de gestión. */
    ADMIN,
    /** Coordinación de INNPRENDE I y II: operación completa, sin la parte técnica. */
    MACONDOLAB,
    /** Docente de la universidad. */
    TEACHER,
    /** Jurado: evalúa proyectos. Puede ser externo a la universidad. */
    JUDGE,
    /** Estudiante: el rol con el que nace toda cuenta registrada. */
    STUDENT;

    /** Si el rol pertenece a la comunidad académica y debe declarar sede y facultad. */
    public boolean requiresAffiliation() {
        return this == TEACHER || this == STUDENT;
    }

    /** Roles con acceso a la gestión de la plataforma (cuentas, catálogos). */
    public boolean isManagement() {
        return this == ADMIN || this == MACONDOLAB;
    }

    /**
     * Si quien tiene este rol puede administrar una cuenta con el rol {@code other}:
     * cambiarle el rol o la adscripción, o restablecer su contraseña. MacondoLab
     * gestiona estudiantes, docentes y jurados; las cuentas de gestión solo las
     * toca un administrador.
     */
    public boolean canManage(Role other) {
        return switch (this) {
            case ADMIN -> true;
            case MACONDOLAB -> !other.isManagement();
            default -> false;
        };
    }

    /** Nombre en español, para los correos. La app tiene el suyo en lib/roles.js. */
    public String label() {
        return switch (this) {
            case ADMIN -> "Administrador";
            case MACONDOLAB -> "MacondoLab";
            case TEACHER -> "Profesor";
            case JUDGE -> "Jurado";
            case STUDENT -> "Estudiante";
        };
    }

    /** Autoridad de Spring Security: ROLE_ADMIN, ROLE_STUDENT... */
    public String authority() {
        return "ROLE_" + name();
    }
}
