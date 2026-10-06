package co.edu.unisimon.expoideas.evaluations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Escala de valoración institucional (Art. 62 del Estatuto Estudiantil de la
 * Universidad Simón Bolívar), como la citan las rúbricas: de 0.0 a 5.0, con un
 * decimal, y se aprueba desde 3.0.
 */
public enum GradeScale {
    FAILING("Deficiente", "0.0"),
    ACCEPTABLE("Aceptable", "3.0"),
    GOOD("Bueno", "4.0"),
    VERY_GOOD("Muy bueno", "4.5"),
    EXCELLENT("Excelente", "5.0");

    private static final BigDecimal PASSING = new BigDecimal("3.0");

    private final String label;
    private final BigDecimal from;

    GradeScale(String label, String from) {
        this.label = label;
        this.from = new BigDecimal(from);
    }

    /** Nombre en español, para los correos y el CSV. La app tiene el suyo. */
    public String label() {
        return label;
    }

    /** El nivel más alto cuyo mínimo alcanza esa nota. */
    public static GradeScale of(BigDecimal grade) {
        GradeScale found = FAILING;
        for (GradeScale level : values()) {
            if (grade.compareTo(level.from) >= 0) {
                found = level;
            }
        }
        return found;
    }

    public static boolean isPassing(BigDecimal grade) {
        return grade.compareTo(PASSING) >= 0;
    }

    /** Promedio simple con un decimal (el medio sube), o vacío si no hay notas. */
    public static Optional<BigDecimal> average(List<BigDecimal> grades) {
        if (grades.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = grades.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(sum.divide(BigDecimal.valueOf(grades.size()), 1, RoundingMode.HALF_UP));
    }
}
