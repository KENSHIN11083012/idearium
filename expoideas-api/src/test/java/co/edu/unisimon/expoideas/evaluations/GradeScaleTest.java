package co.edu.unisimon.expoideas.evaluations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Escala institucional y promedio con un decimal. */
class GradeScaleTest {

    @ParameterizedTest
    @CsvSource({
        "0.0, FAILING",
        "2.9, FAILING",
        "3.0, ACCEPTABLE",
        "3.9, ACCEPTABLE",
        "4.0, GOOD",
        "4.4, GOOD",
        "4.5, VERY_GOOD",
        "4.9, VERY_GOOD",
        "5.0, EXCELLENT"
    })
    void eachGradeFallsInItsLevel(String grade, GradeScale expected) {
        assertThat(GradeScale.of(new BigDecimal(grade))).isEqualTo(expected);
    }

    @Test
    void passingStartsAtThree() {
        assertThat(GradeScale.isPassing(new BigDecimal("3.0"))).isTrue();
        assertThat(GradeScale.isPassing(new BigDecimal("2.9"))).isFalse();
    }

    @Test
    void theAverageIsSimpleWithOneDecimalAndTheHalfGoesUp() {
        // (4.0 + 4.5) / 2 = 4.25
        assertThat(GradeScale.average(grades("4.0", "4.5"))).contains(new BigDecimal("4.3"));
        // (5.0 + 4.5 + 4.0 + 4.5 + 5.0 + 4.0) / 6 = 4.5
        assertThat(GradeScale.average(grades("5.0", "4.5", "4.0", "4.5", "5.0", "4.0")))
                .contains(new BigDecimal("4.5"));
        // (3.0 + 3.0 + 1.5) / 3 = 2.5
        assertThat(GradeScale.average(grades("3.0", "3.0", "1.5"))).contains(new BigDecimal("2.5"));
        assertThat(GradeScale.average(List.of())).isEmpty();
    }

    private static List<BigDecimal> grades(String... values) {
        return Stream.of(values).map(BigDecimal::new).toList();
    }
}
