package co.edu.unisimon.expoideas.editions;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Una vuelta de la Expo, con sus fechas: "Expoideas 2026-2". Los proyectos se
 * inscriben mientras la inscripción está abierta y suben sus entregables hasta
 * el cierre de entregas, que es posterior.
 *
 * <p>Las fechas son días completos en la zona horaria de Colombia y los dos
 * extremos cuentan: el último día de inscripción todavía se puede inscribir.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "editions")
public class Edition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "registration_opens_on", nullable = false)
    private LocalDate registrationOpensOn;

    @Column(name = "registration_closes_on", nullable = false)
    private LocalDate registrationClosesOn;

    @Column(name = "submission_closes_on", nullable = false)
    private LocalDate submissionClosesOn;

    @Setter(AccessLevel.NONE)
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Las dos cátedras con su configuración; viven y mueren con la edición. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "edition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("track")
    private List<EditionTrack> tracks = new ArrayList<>();

    /** Configuración de una cátedra en esta edición, si está definida. */
    public Optional<EditionTrack> track(Track track) {
        return tracks.stream().filter(item -> item.getTrack() == track).findFirst();
    }

    /**
     * Deja la configuración de esa cátedra con esos límites, creándola si no
     * estaba. Mantiene las dos puntas de la relación para que JPA la persista.
     */
    public void setTrackSettings(Track track, int minMembers, int maxMembers) {
        EditionTrack settings = track(track).orElseGet(() -> {
            EditionTrack created = new EditionTrack();
            created.setEdition(this);
            created.setTrack(track);
            tracks.add(created);
            return created;
        });
        settings.setMinMembers(minMembers);
        settings.setMaxMembers(maxMembers);
    }

    /** Si ese día se pueden inscribir proyectos. */
    public boolean isRegistrationOpenOn(LocalDate day) {
        return !day.isBefore(registrationOpensOn) && !day.isAfter(registrationClosesOn);
    }

    /** Si ese día se pueden subir entregables: desde que abre la inscripción hasta el cierre de entregas. */
    public boolean isSubmissionOpenOn(LocalDate day) {
        return !day.isBefore(registrationOpensOn) && !day.isAfter(submissionClosesOn);
    }

    /** Si las fechas de esta edición se pisan con las de otra. */
    public boolean overlaps(Edition other) {
        return !registrationOpensOn.isAfter(other.submissionClosesOn)
                && !other.registrationOpensOn.isAfter(submissionClosesOn);
    }
}
