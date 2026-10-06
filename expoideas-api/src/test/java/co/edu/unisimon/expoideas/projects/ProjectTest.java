package co.edu.unisimon.expoideas.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unisimon.expoideas.editions.Edition;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.users.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** El equipo de un proyecto: quién es el líder y cuántos lugares están tomados. */
class ProjectTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 22, 15);

    @Test
    void theLeaderAndTheInvitedPeopleMakeUpTheTeam() {
        Project project = project();
        User leader = account(1);
        User invited = account(2);

        project.addMember(leader, MemberRole.LEADER, MembershipStatus.ACCEPTED, NOW);
        ProjectMember invitation = project.addMember(invited, MemberRole.MEMBER, MembershipStatus.INVITED, NOW);

        assertThat(project.leader().getUser()).isEqualTo(leader);
        assertThat(project.memberOf(invited)).contains(invitation);
        assertThat(project.memberOf(account(3))).isEmpty();
        // Una invitación sin responder también ocupa un lugar.
        assertThat(project.occupiedSeats()).isEqualTo(2);
    }

    @Test
    void acceptingAnInvitationLeavesItsDate() {
        Project project = project();
        ProjectMember invitation = project.addMember(account(2), MemberRole.MEMBER, MembershipStatus.INVITED, NOW);
        assertThat(invitation.getRespondedAt()).isNull();

        invitation.accept(NOW);

        assertThat(invitation.isAccepted()).isTrue();
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
    }

    @Test
    void leavingFreesTheSeat() {
        Project project = project();
        project.addMember(account(1), MemberRole.LEADER, MembershipStatus.ACCEPTED, NOW);
        ProjectMember member = project.addMember(account(2), MemberRole.MEMBER, MembershipStatus.ACCEPTED, NOW);

        project.removeMember(member);

        assertThat(project.occupiedSeats()).isEqualTo(1);
    }

    @Test
    void theProjectKnowsTheSettingsOfItsTrack() {
        Project project = project();

        assertThat(project.trackSettings().getMaxMembers()).isEqualTo(5);
    }

    @Test
    void aProjectWithoutALeaderIsABug() {
        Project project = project();
        project.addMember(account(2), MemberRole.MEMBER, MembershipStatus.ACCEPTED, NOW);

        assertThatThrownBy(project::leader).isInstanceOf(IllegalStateException.class);
    }

    private static Project project() {
        Edition edition = new Edition();
        edition.setId(1);
        edition.setName("Expoideas 2026-2");
        edition.setRegistrationOpensOn(LocalDate.of(2026, 11, 3));
        edition.setRegistrationClosesOn(LocalDate.of(2026, 11, 14));
        edition.setSubmissionClosesOn(LocalDate.of(2026, 11, 28));
        edition.setTrackSettings(Track.INNPRENDE_I, 2, 5);

        Project project = new Project();
        project.setId(10);
        project.setEdition(edition);
        project.setTrack(Track.INNPRENDE_I);
        project.setTitle("BioSensor");
        project.setSummary("Sensores para detectar plagas antes de que se vean.");
        return project;
    }

    private static User account(int id) {
        return User.builder()
                .id(id)
                .firstName("Persona")
                .lastName(String.valueOf(id))
                .email("persona" + id + "@unisimon.edu.co")
                .passwordHash("hash")
                .build();
    }
}
