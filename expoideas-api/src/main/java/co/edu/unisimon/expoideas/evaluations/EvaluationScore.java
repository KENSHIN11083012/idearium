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

/** El nivel que el jurado eligió en un criterio, con su observación. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "evaluation_scores")
public class EvaluationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private RubricCriterion criterion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private RubricLevel level;

    /**
     * Lo que valía ese nivel cuando el jurado lo eligió. La nota sale de aquí y
     * no del nivel: si después se corrige un valor de la rúbrica, lo que ya
     * estaba calificado no cambia solo.
     */
    @Column(name = "score_value", nullable = false, precision = 2, scale = 1)
    private BigDecimal scoreValue;

    /** Opcional, salvo que el nivel quede por debajo de 3.0. */
    @Column(length = 500)
    private String comment;
}
