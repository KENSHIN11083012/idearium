package co.edu.unisimon.expoideas.evaluations;

import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.users.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * La calificación que un jurado le pone a un proyecto: un nivel por criterio de
 * la rúbrica, o la marca de que el equipo no asistió. El jurado puede corregirla;
 * siempre hay una sola por jurado y proyecto.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "evaluations")
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "juror_id", nullable = false)
    private User juror;

    /** El equipo no asistió a la sustentación: vale 0.0 y no lleva niveles. */
    @Column(nullable = false)
    private boolean absent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvaluationScore> scores = new ArrayList<>();

    public static Evaluation of(Project project, User juror) {
        Evaluation evaluation = new Evaluation();
        evaluation.setProject(project);
        evaluation.setJuror(juror);
        return evaluation;
    }

    /** El equipo no asistió: se quitan los niveles que hubiera. */
    public void markAbsent(LocalDateTime now) {
        absent = true;
        scores.clear();
        touch(now);
    }

    /**
     * Pone o cambia el nivel de un criterio, con lo que ese nivel vale ahora. Se
     * actualiza la fila que ya existe en vez de borrar y crear: solo hay una por
     * criterio.
     */
    public void score(RubricCriterion criterion, RubricLevel level, String comment, LocalDateTime now) {
        absent = false;
        EvaluationScore score = scoreOf(criterion).orElseGet(() -> {
            EvaluationScore created = new EvaluationScore();
            created.setEvaluation(this);
            created.setCriterion(criterion);
            scores.add(created);
            return created;
        });
        score.setLevel(level);
        score.setScoreValue(level.getScore());
        score.setComment(comment);
        touch(now);
    }

    public Optional<EvaluationScore> scoreOf(RubricCriterion criterion) {
        return scores.stream()
                .filter(score -> score.getCriterion().getId().equals(criterion.getId()))
                .findFirst();
    }

    /**
     * Promedio simple de los criterios, con un decimal; 0.0 si el equipo no
     * asistió. Cada criterio cuenta por lo que valía su nivel al calificarlo.
     */
    public BigDecimal grade() {
        if (absent) {
            return new BigDecimal("0.0");
        }
        List<BigDecimal> values =
                scores.stream().map(EvaluationScore::getScoreValue).toList();
        return GradeScale.average(values).orElse(new BigDecimal("0.0"));
    }

    /**
     * Los niveles son filas aparte: sin tocar la evaluación, cambiar solo un nivel
     * no actualizaría su fecha.
     */
    private void touch(LocalDateTime now) {
        updatedAt = now;
    }
}
