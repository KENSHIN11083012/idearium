package co.edu.unisimon.expoideas.users;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lee el listado de la cátedra desde un CSV, como lo exporta Excel: con BOM o
 * sin él, en UTF-8 o en la codificación de Windows, separado por punto y coma o
 * por coma, y con las columnas en cualquier orden. Las reconoce por su título:
 * correo y rol son obligatorias; nombres y apellidos, opcionales.
 *
 * <p>Solo lee: qué correos valen, qué roles existen y qué pasa con los
 * repetidos lo decide {@link RosterService}.
 */
final class RosterCsv {

    /** Una fila con sus celdas, ya recortadas, y el número de línea para explicar los rechazos. */
    record Row(int line, String email, String role, String firstName, String lastName) {}

    /** Títulos aceptados para cada columna, sin tildes ni mayúsculas. */
    private static final Map<String, List<String>> HEADERS = Map.of(
            "email", List.of("correo", "correo electronico", "correo institucional", "email", "e-mail", "mail"),
            "role", List.of("rol", "role", "tipo", "perfil"),
            "firstName", List.of("nombres", "nombre", "first name", "first_name", "firstname"),
            "lastName", List.of("apellidos", "apellido", "last name", "last_name", "lastname"));

    private RosterCsv() {}

    /**
     * @throws IllegalArgumentException si el archivo está vacío o la cabecera no trae correo y rol
     */
    static List<Row> parse(byte[] content) {
        List<String> lines = decode(content).lines().toList();
        if (lines.isEmpty() || lines.stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String header = lines.getFirst();
        char separator = header.chars().filter(c -> c == ';').count()
                        >= header.chars().filter(c -> c == ',').count()
                ? ';'
                : ',';
        List<String> titles =
                split(header, separator).stream().map(RosterCsv::key).toList();
        int email = column(titles, "email");
        int role = column(titles, "role");
        if (email < 0 || role < 0) {
            throw new IllegalArgumentException(
                    "La primera fila debe tener las columnas «correo» y «rol» (y, si quieres, «nombres» y «apellidos»)");
        }
        int firstName = column(titles, "firstName");
        int lastName = column(titles, "lastName");

        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            List<String> cells = split(lines.get(index), separator);
            rows.add(new Row(
                    index + 1, cell(cells, email), cell(cells, role), cell(cells, firstName), cell(cells, lastName)));
        }
        return rows;
    }

    /** UTF-8 si el archivo es UTF-8 válido; si no, la codificación de Windows en español, que es lo que exporta Excel. */
    private static String decode(byte[] content) {
        byte[] bytes = content;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            bytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /** Separa una línea respetando las comillas, como las pone Excel cuando una celda trae el separador. */
    private static List<String> split(String line, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == separator && !quoted) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }

    private static int column(List<String> titles, String field) {
        for (int index = 0; index < titles.size(); index++) {
            if (HEADERS.get(field).contains(titles.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static String cell(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        return Optional.of(cells.get(index)).filter(value -> !value.isEmpty()).orElse(null);
    }

    /** "Correo electrónico" -> "correo electronico": para reconocer los títulos como los escriba la gente. */
    static String key(String title) {
        String plain = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
