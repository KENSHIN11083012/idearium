package co.edu.unisimon.expoideas.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración propia de la aplicación, bajo el prefijo {@code expoideas}. Los
 * valores llegan por variables de entorno (ver application.properties); si falta
 * el secreto del JWT, la API no arranca.
 */
@Validated
@ConfigurationProperties("expoideas")
public record ExpoideasProperties(
        @NotNull @Valid JwtSettings jwt,
        @DefaultValue CorsSettings cors,
        @DefaultValue FilesSettings files,
        @DefaultValue MailSettings mail,
        @DefaultValue LoginSettings login,
        @DefaultValue AdminSettings admin,
        @DefaultValue("") String appUrl) {

    /**
     * @param secret     clave HMAC en Base64, de al menos 256 bits (JWT_SECRET)
     * @param expiration vigencia de la sesión (JWT_EXPIRATION; un número sin unidad son milisegundos)
     */
    public record JwtSettings(
            @NotBlank String secret, @DefaultValue("4h") Duration expiration) {}

    /** Orígenes que pueden llamar a la API desde el navegador (ALLOWED_ORIGINS). Vacío: solo el mismo origen. */
    public record CorsSettings(@DefaultValue List<String> allowedOrigins) {}

    /** Carpeta del contenido de los archivos subidos (FILES_DIR). */
    public record FilesSettings(@DefaultValue("uploads") Path directory) {}

    /**
     * Freno a la prueba de contraseñas contra una cuenta.
     *
     * @param maxFailedAttempts fallos seguidos que bloquean la cuenta (LOGIN_MAX_FAILED_ATTEMPTS)
     * @param lockDuration      cuánto dura el bloqueo (LOGIN_LOCK_DURATION)
     */
    public record LoginSettings(
            @DefaultValue("5") @Min(1) int maxFailedAttempts,
            @DefaultValue("5m") Duration lockDuration) {}

    /**
     * El primer administrador de una instalación nueva (ver InitialAdmin). Solo cuenta
     * mientras no haya ningún administrador.
     *
     * @param email    el correo de esa cuenta (ADMIN_EMAIL); vacío, no se crea ninguna
     * @param password su contraseña temporal (ADMIN_PASSWORD)
     */
    public record AdminSettings(
            @DefaultValue("") String email,
            @DefaultValue("") String password) {}

    /**
     * Envío de los correos. El servidor SMTP va en spring.mail.*.
     *
     * @param from       remitente (MAIL_FROM)
     * @param attempts   veces que se intenta un envío antes de darlo por perdido (MAIL_ATTEMPTS)
     * @param retryDelay espera antes del segundo intento; antes del tercero, el doble, y así (MAIL_RETRY_DELAY)
     */
    public record MailSettings(
            @DefaultValue("no-reply@unisimon.edu.co") String from,
            @DefaultValue("3") @Min(1) int attempts,
            @DefaultValue("5s") Duration retryDelay) {}
}
