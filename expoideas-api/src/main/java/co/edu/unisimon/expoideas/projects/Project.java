package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.catalogs.PrototypeType;
import co.edu.unisimon.expoideas.catalogs.Sector;
import co.edu.unisimon.expoideas.editions.Edition;
import co.edu.unisimon.expoideas.editions.EditionTrack;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.users.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
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
 * Un proyecto inscrito en una cátedra de una edición. La edición y la cátedra no
 * cambian: si el grupo vuelve a participar, inscribe otro proyecto.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Track track;

    @Column(nullable = false, length = 150)
    private String title;

    /** Propuesta de valor: qué problema resuelve, con qué y para quién. */
    @Column(nullable = false, length = 500)
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    /** Solo en INNPRENDE II, y solo cuando MacondoLab ya cargó el catálogo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prototype_type_id")
    private PrototypeType prototypeType;

    /** Docente que acompaña al grupo, elegido por el líder. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    /** Lo pone el profesor del grupo o la gestión al cerrar las entregas; null mientras tanto. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProjectResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_set_by")
    private User resultSetBy;

    @Column(name = "result_set_at")
    private LocalDateTime resultSetAt;

    @Setter(AccessLevel.NONE)
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** El equipo: el líder primero y después quienes fue invitando. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("teamRole, invitedAt")
    private List<ProjectMember> members = new ArrayList<>();

    /**
     * Agrega a alguien al equipo con ese papel y estado, y devuelve la fila. Quien
     * entra ya aceptado (el líder) lleva {@code now} como fecha de respuesta.
     */
    public ProjectMember addMember(User user, MemberRole teamRole, MembershipStatus status, LocalDateTime now) {
        ProjectMember member = new ProjectMember();
        member.setProject(this);
        member.setEditionId(edition.getId());
        member.setUser(user);
        member.setTeamRole(teamRole);
        member.setStatus(status);
        if (status == MembershipStatus.ACCEPTED) {
            member.setRespondedAt(now);
        }
        members.add(member);
        return member;
    }

    /** Deja constancia del resultado y de quién lo puso. */
    public void setResult(ProjectResult result, User actor, LocalDateTime now) {
        this.result = result;
        this.resultSetBy = actor;
        this.resultSetAt = now;
    }

    /** Los integrantes que aceptaron: los que cuentan para el resultado. */
    public List<User> acceptedMembers() {
        return members.stream()
                .filter(ProjectMember::isAccepted)
                .map(ProjectMember::getUser)
                .toList();
    }

    public void removeMember(ProjectMember member) {
        members.remove(member);
    }

    public Optional<ProjectMember> memberOf(User user) {
        return members.stream()
                .filter(member -> member.getUser().getId().equals(user.getId()))
                .findFirst();
    }

    public ProjectMember leader() {
        return members.stream()
                .filter(ProjectMember::isLeader)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El proyecto " + id + " se quedó sin líder"));
    }

    /**
     * Cuántos lugares del equipo están tomados: los aceptados y las invitaciones
     * sin responder, porque una invitación reserva el cupo.
     */
    public int occupiedSeats() {
        return members.size();
    }

    /** Configuración de la cátedra en la que está inscrito (tamaño del grupo). */
    public EditionTrack trackSettings() {
        return edition.track(track)
                .orElseThrow(() -> new IllegalStateException(
                        "La edición " + edition.getId() + " no tiene configurada la cátedra " + track));
    }
}
