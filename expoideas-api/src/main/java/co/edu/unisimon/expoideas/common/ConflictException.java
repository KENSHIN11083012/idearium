package co.edu.unisimon.expoideas.common;

/**
 * La operación choca con un dato que ya existe (p. ej. un correo registrado).
 * GlobalExceptionHandler la traduce a 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
