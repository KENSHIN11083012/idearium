package co.edu.unisimon.expoideas.evaluations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un criterio de la rúbrica, con sus niveles de menor a mayor. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rubric_criteria")
public class RubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_id", nullable = false)
    private Rubric rubric;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String name;

    /** El nombre corto de la tabla de retroalimentación, para listas y resúmenes. */
    @Column(name = "short_name", nullable = false, length = 150)
    private String shortName;

    @OneToMany(mappedBy = "criterion")
    @OrderBy("position ASC")
    private List<RubricLevel> levels = new ArrayList<>();

    /** El nivel con ese id, si es de este criterio. */
    public Optional<RubricLevel> level(Integer levelId) {
        return levels.stream().filter(level -> level.getId().equals(levelId)).findFirst();
    }
}
