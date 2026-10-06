package co.edu.unisimon.expoideas.evaluations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un nivel de un criterio: cuánto vale y qué describe. El valor es del nivel
 * de ese criterio, no de la columna: en la rúbrica del póster el mismo nivel
 * vale distinto según el criterio.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rubric_levels")
public class RubricLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private RubricCriterion criterion;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal score;

    @Column(nullable = false, length = 1000)
    private String description;

    /** Por debajo de la nota mínima: la observación del jurado deja de ser opcional. */
    public boolean isFailing() {
        return !GradeScale.isPassing(score);
    }
}
