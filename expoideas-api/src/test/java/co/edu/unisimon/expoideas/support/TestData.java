package co.edu.unisimon.expoideas.support;

import co.edu.unisimon.expoideas.catalogs.Campus;
import co.edu.unisimon.expoideas.catalogs.Faculty;
import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.AdminSettings;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.CorsSettings;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.FilesSettings;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.JwtSettings;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.LoginSettings;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.MailSettings;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Datos de prueba compartidos. */
public final class TestData {

    /** Clave HMAC de 256 bits en Base64, solo para pruebas. */
    public static final String JWT_SECRET = "ZXhwb2lkZWFzLWludGVncmF0aW9uLXRlc3RzLWtleSE=";

    /** Cumple la regla de contraseñas: 8+ caracteres, número y símbolo. */
    public static final String PASSWORD = "Segura#2026";

    public static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    public static final byte[] WEBP = concat(ascii("RIFF"), new byte[] {36, 0, 0, 0}, ascii("WEBPVP8 "));
    public static final byte[] PDF = ascii("%PDF-1.7\n");
    /** Cabecera local de un ZIP (PK\003\004) seguida del nombre de la entrada que define el formato. */
    public static final byte[] ZIP = concat(new byte[] {0x50, 0x4B, 0x03, 0x04}, new byte[26], ascii("mimetype"));

    public static final byte[] DOCX =
            concat(new byte[] {0x50, 0x4B, 0x03, 0x04}, new byte[26], ascii("word/document.xml"));
    public static final byte[] PPTX =
            concat(new byte[] {0x50, 0x4B, 0x03, 0x04}, new byte[26], ascii("ppt/presentation.xml"));

    private TestData() {}

    /** Cuenta al día (sin pasos de primer ingreso) con ese rol: con nombre y, si el rol la lleva, adscripción. */
    public static User user(int id, String email, Role role) {
        User.UserBuilder user = User.builder()
                .id(id)
                .firstName("Nombre")
                .lastName("Apellido")
                .email(email)
                .passwordHash("hash-actual")
                .role(role)
                .dataConsent(true);
        if (role.requiresAffiliation()) {
            user.campus(new Campus(1, "Barranquilla")).faculty(new Faculty(10, "Ingeniería"));
        }
        return user.build();
    }

    public static ExpoideasProperties properties(Path filesDirectory) {
        return new ExpoideasProperties(
                new JwtSettings(JWT_SECRET, Duration.ofMinutes(1)),
                new CorsSettings(List.of()),
                new FilesSettings(filesDirectory),
                // Sin espera entre intentos: las pruebas no duermen.
                new MailSettings("expoideas@pruebas.local", 3, Duration.ZERO),
                new LoginSettings(5, Duration.ofMinutes(5)),
                new AdminSettings("", ""),
                "");
    }

    public static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
