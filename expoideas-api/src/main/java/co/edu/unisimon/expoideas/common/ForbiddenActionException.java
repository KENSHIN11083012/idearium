package co.edu.unisimon.expoideas.common;

/**
 * La persona tiene sesión y acceso a la ruta, pero su rol no le permite esta
 * operación concreta (p. ej. MacondoLab editando a un administrador, o alguien
 * cambiando su propio rol). GlobalExceptionHandler la traduce a 403 con el
 * motivo en {@code detail}.
 */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
