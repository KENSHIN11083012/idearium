package co.edu.unisimon.expoideas.common;

import co.edu.unisimon.expoideas.security.OnboardingRequiredException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejo centralizado de errores. Toda respuesta de error de la API sale en
 * formato Problem Details (RFC 9457, {@code application/problem+json}):
 * {@code type}, {@code title}, {@code status}, {@code detail} e {@code instance}.
 * El frontend muestra {@code detail}; los errores de validación añaden
 * {@code fields} con el mensaje de cada campo.
 *
 * <p>Las excepciones propias de Spring MVC (ruta inexistente, método no
 * soportado, JSON mal formado, archivo demasiado grande...) las resuelve
 * {@link ResponseEntityExceptionHandler}; sus textos en español están en
 * {@code messages.properties}. Los rechazos de Spring Security y del primer
 * ingreso llegan aquí desde los filtros de seguridad.
 *
 * <p>Nunca se envían trazas al cliente: van al log del servidor.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Códigos de error de MySQL para cada clase de restricción.
    private static final int MYSQL_COLUMN_CANNOT_BE_NULL = 1048;
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;
    private static final int MYSQL_DATA_TOO_LONG = 1406;
    private static final int MYSQL_ROW_IS_REFERENCED = 1451;
    private static final int MYSQL_NO_REFERENCED_ROW = 1452;
    private static final int MYSQL_CHECK_VIOLATED = 3819;

    /** «for key 'users.uk_users_email'», «CONSTRAINT `fk_x`» o «Check constraint 'chk_x'». */
    private static final Pattern CONSTRAINT_NAME =
            Pattern.compile("(?:for key|CONSTRAINT|Check constraint) ['`]([\\w.]+)['`]");

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ProblemDetail handleBadCredentials() {
        // Mismo mensaje para usuario inexistente y contraseña incorrecta:
        // distinguirlos permitiría enumerar cuentas.
        return problem(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    /**
     * Cuenta suspendida por la gestión. Se dice sin rodeos: quien intenta entrar
     * tiene que saber a quién acudir, y el registro ya deja ver si un correo tiene cuenta.
     */
    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleSuspended() {
        return problem(
                HttpStatus.UNAUTHORIZED, "Tu cuenta está suspendida. Comunícate con la coordinación de la cátedra.");
    }

    /** Demasiados intentos fallidos: la cuenta está bloqueada un rato. El mensaje dice cuánto. */
    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(LockedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleUnauthenticated() {
        return problem(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión para acceder a este recurso");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied() {
        return problem(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción");
    }

    /** Regla de negocio sobre roles: a diferencia del 403 genérico, explica el motivo. */
    @ExceptionHandler(ForbiddenActionException.class)
    public ProblemDetail handleForbiddenAction(ForbiddenActionException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Primer ingreso sin completar: el cliente lleva a la persona a resolver {@code pendingSteps}. */
    @ExceptionHandler(OnboardingRequiredException.class)
    public ProblemDetail handleOnboardingRequired(OnboardingRequiredException ex) {
        ProblemDetail body = problem(HttpStatus.FORBIDDEN, ex.getMessage());
        body.setProperty("pendingSteps", ex.getPendingSteps());
        return body;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * La base rechazó el cambio por una de sus restricciones. No todas significan lo
     * mismo: un nombre repetido es un conflicto que la persona puede resolver; un
     * dato que no cabe o que no cumple una regla es un dato inválido; una
     * referencia rota es que alguien borró o usó eso mientras tanto. Decirle «ya
     * existe» a todo manda a buscar un duplicado que no hay.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        int code = ex.getMostSpecificCause() instanceof SQLException sql ? sql.getErrorCode() : 0;
        // Sin el mensaje de MySQL: trae el valor que chocó, que puede ser un correo.
        log.warn("La base rechazó el cambio (código {}, restricción {})", code, constraintOf(ex));
        return switch (code) {
            case MYSQL_DUPLICATE_ENTRY -> problem(HttpStatus.CONFLICT, "Ya existe un registro con esos datos");
            case MYSQL_ROW_IS_REFERENCED ->
                problem(HttpStatus.CONFLICT, "No se puede eliminar ni cambiar: hay otros datos que dependen de este");
            case MYSQL_NO_REFERENCED_ROW ->
                problem(
                        HttpStatus.CONFLICT,
                        "Uno de los datos a los que esto hace referencia ya no existe. Recarga la página e inténtalo de nuevo");
            case MYSQL_DATA_TOO_LONG ->
                problem(HttpStatus.BAD_REQUEST, "Uno de los datos es más largo de lo permitido");
            case MYSQL_COLUMN_CANNOT_BE_NULL, MYSQL_CHECK_VIOLATED ->
                problem(HttpStatus.BAD_REQUEST, "Los datos enviados no cumplen las reglas de la plataforma");
            default -> problem(HttpStatus.CONFLICT, "No se pudo guardar: los datos chocan con otros ya guardados");
        };
    }

    /** El nombre de la restricción que saltó, si MySQL lo dice; nunca el valor que chocó. */
    private static String constraintOf(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        Matcher matcher = CONSTRAINT_NAME.matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group(1) : "desconocida";
    }

    /**
     * La base de datos no responde (caída, sin conexiones, consulta que no vuelve).
     * Es un 503 y no un 500: el problema es pasajero y no de la petición, y la app
     * no debe confundirlo con una sesión vencida.
     */
    @ExceptionHandler({
        TransientDataAccessException.class,
        NonTransientDataAccessResourceException.class,
        CannotCreateTransactionException.class
    })
    public ProblemDetail handleDatabaseUnavailable(Exception ex) {
        log.error("La base de datos no responde", ex);
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "La plataforma no está disponible en este momento. Intenta de nuevo en unos minutos.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Validación por campo hecha en el servicio: mismo formato que la de Bean Validation. */
    @ExceptionHandler(InvalidFieldsException.class)
    public ProblemDetail handleInvalidFields(InvalidFieldsException ex) {
        return invalidFields(HttpStatus.BAD_REQUEST, ex.getFields());
    }

    /**
     * La petición decía traer un archivo y no se pudo leer: no es multipart, le falta el
     * separador o viene cortada. El archivo demasiado grande no llega aquí: lo resuelve
     * {@link ResponseEntityExceptionHandler} con su 413.
     */
    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleUnreadableUpload() {
        return problem(
                HttpStatus.BAD_REQUEST, "No pudimos leer el archivo enviado. Vuelve a elegirlo e inténtalo de nuevo.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Error no controlado en {}", request.getDescription(false), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return handleExceptionInternal(ex, invalidFields(status, fields), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "El cuerpo de la petición no es un JSON válido");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail invalidFields(HttpStatusCode status, Map<String, String> fields) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Datos inválidos");
        body.setProperty("fields", fields);
        return body;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
