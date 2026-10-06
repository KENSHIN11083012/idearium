package co.edu.unisimon.expoideas.status;

import java.time.LocalDateTime;

/**
 * Cómo está la instalación, para quien la administra.
 *
 * @param version la etiqueta de la imagen que corre, o «local»
 * @param now     la hora de la plataforma (Colombia), para ver de un vistazo que el reloj está bien
 */
public record StatusResponse(String version, LocalDateTime now, Disk disk, Mail mail) {

    /**
     * El disco donde se guardan los archivos subidos.
     *
     * @param freeBytes   lo que queda libre
     * @param totalBytes  el tamaño del disco
     * @param usedPercent cuánto va ocupado, de 0 a 100
     * @param low         si queda menos del margen con el que la salud pasa a DOWN
     */
    public record Disk(long freeBytes, long totalBytes, int usedPercent, boolean low) {}

    /**
     * El correo de avisos.
     *
     * @param configured        si hay servidor SMTP: sin él no se envía nada
     * @param linksEnabled      si además hay una URL pública: de eso dependen la verificación del
     *                          correo y la recuperación de contraseña
     * @param failedLast24Hours correos que no salieron, tras todos los intentos, en las últimas 24 horas
     * @param lastFailureAt     cuándo fue el último que no salió; null si nunca
     */
    public record Mail(boolean configured, boolean linksEnabled, long failedLast24Hours, LocalDateTime lastFailureAt) {}
}
