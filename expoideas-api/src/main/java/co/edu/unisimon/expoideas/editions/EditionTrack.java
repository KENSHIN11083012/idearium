package co.edu.unisimon.expoideas.editions;

import co.edu.unisimon.expoideas.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lo que MacondoLab configura de una cátedra en una edición: el tamaño del
 * grupo y si las notas ya se publicaron. Además, con qué rúbrica se califica,
 * que queda fijada al crear la edición.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edition_tracks")
public class EditionTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Track track;

    /** Integrantes del grupo, contando al líder. */
    @Column(name = "min_members", nullable = false)
    private int minMembers;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    /**
     * La versión de la rúbrica con la que se califica esta cátedra en esta
     * edición. Solo el id: qué es una rúbrica lo sabe el módulo de evaluaciones.
     * Null si la cátedra no tenía rúbrica cuando se creó la edición.
     */
    @Column(name = "rubric_id")
    private Integer rubricId;

    /** Desde cuándo los equipos ven su nota; null mientras los jurados califican. */
    @Column(name = "grades_published_at")
    private LocalDateTime gradesPublishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grades_published_by")
    private User gradesPublishedBy;

    public boolean isGradesPublished() {
        return gradesPublishedAt != null;
    }

    public void publishGrades(User actor, LocalDateTime when) {
        gradesPublishedAt = when;
        gradesPublishedBy = actor;
    }

    public void hideGrades() {
        gradesPublishedAt = null;
        gradesPublishedBy = null;
    }
}
