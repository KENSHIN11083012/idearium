package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Constancia de que una persona aprobó una cátedra. Nace al aprobar su
 * proyecto, o la registra la gestión a mano (sin proyecto) para quien cursó la
 * cátedra antes de existir la plataforma. Es el prerrequisito de INNPRENDE II.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "track_approvals")
public class TrackApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Track track;

    /** El proyecto aprobado, o null si la aprobación se registró a mano. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** Quién aprobó: el profesor del grupo o alguien de la gestión. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @CreatedDate
    @Column(name = "approved_at", nullable = false, updatable = false)
    private LocalDateTime approvedAt;

    public static TrackApproval of(User user, Track track, Project project, User approvedBy) {
        TrackApproval approval = new TrackApproval();
        approval.setUser(user);
        approval.setTrack(track);
        approval.setProject(project);
        approval.setApprovedBy(approvedBy);
        return approval;
    }
}
