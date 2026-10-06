package co.edu.unisimon.expoideas.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Ninguna fecha de la plataforma sale de la zona horaria del servidor, que la
 * fija TI: todas pasan por el Clock de {@link TimeConfig}, sea inyectado o
 * recibiendo la hora como parámetro.
 */
class ClockUsageTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    private static final Pattern NOW_WITHOUT_CLOCK =
            Pattern.compile("\\b(LocalDateTime|LocalDate|LocalTime|ZonedDateTime|OffsetDateTime)\\.now\\(\\s*\\)");

    @Test
    void noMainClassReadsTheServerClockDirectly() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(ClockUsageTest::offendingLines)
                    .toList();
        }

        assertThat(offenders)
                .as("usa LocalDateTime.now(clock) o recibe la hora como parámetro")
                .isEmpty();
    }

    private static Stream<String> offendingLines(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return IntStream.range(0, lines.size())
                .filter(index -> NOW_WITHOUT_CLOCK.matcher(lines.get(index)).find())
                .mapToObj(index -> SOURCES.relativize(file) + ":" + (index + 1));
    }
}
