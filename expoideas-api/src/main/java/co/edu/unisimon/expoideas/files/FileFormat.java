package co.edu.unisimon.expoideas.files;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formatos que acepta la plataforma, reconocidos por su firma (los primeros
 * bytes) y no por la extensión ni por el Content-Type que declara el navegador,
 * que el cliente controla. Un .exe renombrado a .png no pasa.
 *
 * <p>SVG queda fuera a propósito: es XML y puede llevar scripts. DOCX y PPTX son
 * ZIP con una estructura fija: se reconocen por la firma ZIP y por la entrada
 * que los define (word/document.xml, ppt/presentation.xml); un ZIP cualquiera
 * no pasa.
 */
public enum FileFormat {
    JPEG("image/jpeg", "jpg", "JPG"),
    PNG("image/png", "png", "PNG"),
    WEBP("image/webp", "webp", "WEBP"),
    PDF("application/pdf", "pdf", "PDF"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", "DOCX"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx", "PPTX");

    /** Fotos (perfil, galería, prototipos). */
    public static final Set<FileFormat> IMAGES = Collections.unmodifiableSet(EnumSet.of(JPEG, PNG, WEBP));

    /** Documentos que un equipo entrega: el póster, una carta de validación. */
    public static final Set<FileFormat> DOCUMENTS = Collections.unmodifiableSet(EnumSet.of(PDF));

    /** Plantillas que la gestión ofrece para descargar y diligenciar. */
    public static final Set<FileFormat> TEMPLATES = Collections.unmodifiableSet(EnumSet.of(PDF, DOCX, PPTX));

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_SIGNATURE = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_SIGNATURE = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] DOCX_ENTRY = "word/document.xml".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PPTX_ENTRY = "ppt/presentation.xml".getBytes(StandardCharsets.US_ASCII);

    private final String contentType;
    private final String extension;
    private final String label;

    FileFormat(String contentType, String extension, String label) {
        this.contentType = contentType;
        this.extension = extension;
        this.label = label;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** Formato del contenido según su firma, o vacío si no es ninguno de los aceptados. */
    public static Optional<FileFormat> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_SIGNATURE)) return Optional.of(JPEG);
        if (startsWith(content, 0, PNG_SIGNATURE)) return Optional.of(PNG);
        if (startsWith(content, 0, RIFF_SIGNATURE) && startsWith(content, 8, WEBP_SIGNATURE)) return Optional.of(WEBP);
        if (startsWith(content, 0, PDF_SIGNATURE)) return Optional.of(PDF);
        if (startsWith(content, 0, ZIP_SIGNATURE)) {
            if (contains(content, DOCX_ENTRY)) return Optional.of(DOCX);
            if (contains(content, PPTX_ENTRY)) return Optional.of(PPTX);
        }
        return Optional.empty();
    }

    /** "JPG, PNG o WEBP", para los mensajes de error. */
    public static String describe(Set<FileFormat> formats) {
        String[] labels = formats.stream().sorted().map(format -> format.label).toArray(String[]::new);
        if (labels.length == 1) return labels[0];
        return Arrays.stream(labels, 0, labels.length - 1).collect(Collectors.joining(", ")) + " o "
                + labels[labels.length - 1];
    }

    /** Si la entrada aparece en el ZIP: los nombres de las entradas van en claro en las cabeceras locales. */
    private static boolean contains(byte[] content, byte[] entry) {
        for (int i = 0; i <= content.length - entry.length; i++) {
            if (Arrays.equals(content, i, i + entry.length, entry, 0, entry.length)) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) return false;
        return Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
    }
}
