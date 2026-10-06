package co.edu.unisimon.expoideas.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validación que depende del estado (el rol de la cuenta, la contraseña actual,
 * el contenido de un archivo) y por eso no cabe en las anotaciones de la
 * petición. GlobalExceptionHandler la traduce a un 400 con {@code fields}, igual
 * que los errores de Bean Validation, para que el cliente la muestre en cada campo.
 */
public class InvalidFieldsException extends RuntimeException {

    private final Map<String, String> fields;

    public InvalidFieldsException(Map<String, String> fields) {
        super("Datos inválidos: " + fields);
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** Un solo campo con su mensaje. */
    public InvalidFieldsException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
