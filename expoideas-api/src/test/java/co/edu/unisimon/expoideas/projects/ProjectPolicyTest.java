package co.edu.unisimon.expoideas.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.ForbiddenActionException;
import co.edu.unisimon.expoideas.editions.Edition;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Prerrequisito de INNPRENDE II, exclusión entre cátedras y cuándo se registra el resultado. */
@ExtendWith(MockitoExtension.class)
class ProjectPolicyTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TrackApprovalRepository approvalRepository;

    @Mock
    private ProjectVisibilityRule rule;

    private ProjectPolicy policy;
    private Edition edition;
    private final User ana = TestData.user(1, "ana@unisimon.edu.co", Role.STUDENT);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(BOGOTA).toInstant(), BOGOTA);
        policy = new ProjectPolicy(userRepository, projectRepository, approvalRepository, clock, List.of(rule));
        edition = mock(Edition.class);
        lenient().when(edition.getId()).thenReturn(5);
    }

    @Nested
    class Eligibility {

        @Test
        void trackOneOnlyAsksNotToBeOnTrackTwoOfTheSameEdition() {
            when(projectRepository.isOnATeamOfAnotherTrack(5, Track.INNPRENDE_I, 1))
                    .thenReturn(false);

            assertThatCode(() -> policy.requireEligibleFor(ana, edition, Track.INNPRENDE_I))
                    .doesNotThrowAnyException();
        }

        @Test
        void beingOnTheOtherTrackOfTheSameEditionIsAConflict() {
            when(projectRepository.isOnATeamOfAnotherTrack(5, Track.INNPRENDE_II, 1))
                    .thenReturn(true);

            assertThatThrownBy(() -> policy.requireEligibleFor(ana, edition, Track.INNPRENDE_II))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("misma edición");
        }

        @Test
        void trackTwoNeedsAnApprovedTrackOne() {
            when(projectRepository.isOnATeamOfAnotherTrack(any(), any(), any())).thenReturn(false);
            when(approvalRepository.existsByUserIdAndTrack(1, Track.INNPRENDE_I))
                    .thenReturn(false);

            assertThatThrownBy(() -> policy.requireEligibleFor(ana, edition, Track.INNPRENDE_II))
                    .isInstanceOf(ForbiddenActionException.class)
                    .hasMessageContaining("aprobado INNPRENDE I");
        }

        @Test
        void withTrackOneApprovedTrackTwoIsOpen() {
            when(projectRepository.isOnATeamOfAnotherTrack(any(), any(), any())).thenReturn(false);
            when(approvalRepository.existsByUserIdAndTrack(eq(1), eq(Track.INNPRENDE_I)))
                    .thenReturn(true);

            assertThatCode(() -> policy.requireEligibleFor(ana, edition, Track.INNPRENDE_II))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Visibility {

        private final User teacher = TestData.user(7, "carlos@unisimon.edu.co", Role.TEACHER);
        private final User judge = TestData.user(8, "marta@empresa.com", Role.JUDGE);
        private final Project project = new Project();

        @BeforeEach
        void project() {
            project.setEdition(edition);
            project.setTeacher(teacher);
            project.addMember(ana, MemberRole.LEADER, MembershipStatus.ACCEPTED, TODAY.atStartOfDay());
        }

        @Test
        void theTeamTheTeacherAndManagementSeeIt() {
            assertThat(policy.canView(project, ana)).isTrue();
            assertThat(policy.canView(project, teacher)).isTrue();
            assertThat(policy.canView(project, TestData.user(9, "c@unisimon.edu.co", Role.MACONDOLAB)))
                    .isTrue();
        }

        @Test
        void whoIsOnlyInvitedSeesTheProjectButNotItsDeliverables() {
            User invited = TestData.user(4, "luis@unisimon.edu.co", Role.STUDENT);
            project.addMember(invited, MemberRole.MEMBER, MembershipStatus.INVITED, TODAY.atStartOfDay());

            // La ficha, para decidir si acepta; el trabajo del equipo, cuando sea del equipo.
            assertThat(policy.canView(project, invited)).isTrue();
            assertThat(policy.canSeeDeliverables(project, invited)).isFalse();

            project.memberOf(invited).orElseThrow().accept(TODAY.atStartOfDay());
            assertThat(policy.canSeeDeliverables(project, invited)).isTrue();

            assertThat(policy.canSeeDeliverables(project, ana)).isTrue();
            assertThat(policy.canSeeDeliverables(project, teacher)).isTrue();
            when(rule.canView(project, judge)).thenReturn(true);
            assertThat(policy.canSeeDeliverables(project, judge)).isTrue();
        }

        @Test
        void anyoneElseOnlyIfARuleSaysSo() {
            when(rule.canView(project, judge)).thenReturn(false);
            assertThat(policy.canView(project, judge)).isFalse();

            when(rule.canView(project, judge)).thenReturn(true);
            assertThat(policy.canView(project, judge)).isTrue();
        }
    }

    @Nested
    class Result {

        @Test
        void isRegisteredOnlyAfterTheSubmissionDeadline() {
            when(edition.getSubmissionClosesOn()).thenReturn(TODAY);
            assertThatThrownBy(() -> policy.requireSubmissionClosed(edition))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("después del cierre");

            when(edition.getSubmissionClosesOn()).thenReturn(TODAY.minusDays(1));
            assertThatCode(() -> policy.requireSubmissionClosed(edition)).doesNotThrowAnyException();
        }

        @Test
        void onlyTheTeacherOfTheGroupOrManagementSetIt() {
            User teacher = TestData.user(7, "carlos@unisimon.edu.co", Role.TEACHER);
            User otherTeacher = TestData.user(8, "otro@unisimon.edu.co", Role.TEACHER);
            User macondolab = TestData.user(9, "coordinacion@unisimon.edu.co", Role.MACONDOLAB);
            Project project = new Project();
            project.setTeacher(teacher);

            assertThatCode(() -> policy.requireTeacherOrManagement(project, teacher))
                    .doesNotThrowAnyException();
            assertThatCode(() -> policy.requireTeacherOrManagement(project, macondolab))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> policy.requireTeacherOrManagement(project, otherTeacher))
                    .isInstanceOf(ForbiddenActionException.class);
            assertThatThrownBy(() -> policy.requireTeacherOrManagement(project, ana))
                    .isInstanceOf(ForbiddenActionException.class);
        }
    }
}
