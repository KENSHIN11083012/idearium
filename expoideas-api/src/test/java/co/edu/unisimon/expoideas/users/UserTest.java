package co.edu.unisimon.expoideas.users;

import static co.edu.unisimon.expoideas.users.OnboardingStep.CHANGE_PASSWORD;
import static co.edu.unisimon.expoideas.users.OnboardingStep.COMPLETE_PROFILE;
import static co.edu.unisimon.expoideas.users.OnboardingStep.DATA_CONSENT;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.catalogs.Faculty;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final String EMAIL = "ana.perez@unisimon.edu.co";

    /** Cuenta con todo resuelto: nombre, adscripción y autorización de datos. */
    private static User.UserBuilder complete(Role role) {
        return User.builder()
                .email(EMAIL)
                .firstName("Ana")
                .lastName("Pérez")
                .role(role)
                .faculty(new Faculty(1, "Ingeniería"))
                .dataConsent(true);
    }

    @Test
    void completeAccountHasNoPendingSteps() {
        assertThat(complete(Role.STUDENT).build().pendingSteps()).isEmpty();
    }

    @Test
    void accountCreatedByManagementAsksForPasswordThenConsent() {
        User user =
                complete(Role.JUDGE).mustChangePassword(true).dataConsent(false).build();

        assertThat(user.pendingSteps()).containsExactly(CHANGE_PASSWORD, DATA_CONSENT);
    }

    @Test
    void resetPasswordOnlyAsksToChangeIt() {
        User user = complete(Role.STUDENT).mustChangePassword(true).build();

        assertThat(user.pendingSteps()).containsExactly(CHANGE_PASSWORD);
    }

    @Test
    void withoutRecordedConsentItAsksForIt() {
        assertThat(complete(Role.STUDENT).dataConsent(false).build().pendingSteps())
                .containsExactly(DATA_CONSENT);
    }

    @Test
    void consentKeepsTheOriginalDate() {
        User user = User.builder().build();

        LocalDateTime first = LocalDateTime.of(2026, 9, 1, 10, 0);
        user.giveDataConsent(first);
        user.giveDataConsent(first.plusDays(1));

        assertThat(user.isDataConsent()).isTrue();
        assertThat(user.getDataConsentAt()).isEqualTo(first);
    }

    @Test
    void verifyingTheEmailIsTheFirstStepAndGivesTheRoleReservedByTheRoster() {
        User user = complete(Role.STUDENT)
                .emailVerificationPending(true)
                .pendingRole(Role.TEACHER)
                .build();
        assertThat(user.pendingSteps()).containsExactly(OnboardingStep.VERIFY_EMAIL);
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 8, 0);

        user.verifyEmail(now);

        assertThat(user.pendingSteps()).isEmpty();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(now);
        assertThat(user.getRole()).isEqualTo(Role.TEACHER);
        assertThat(user.getPendingRole()).isNull();
    }

    @Test
    void theFifthFailedLoginInARowLocksTheAccountAndTheCountStartsOver() {
        User user = User.builder().build();
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 8, 0);
        LocalDateTime until = now.plusMinutes(5);

        for (int attempt = 1; attempt <= 4; attempt++) {
            user.registerFailedLogin(5, until);
        }
        assertThat(user.isLockedAt(now)).isFalse();

        user.registerFailedLogin(5, until);

        assertThat(user.isLockedAt(now)).isTrue();
        assertThat(user.isLockedAt(until)).isFalse();
        assertThat(user.getFailedLogins()).isZero();
    }

    @Test
    void clearingTheLoginFailuresAlsoLiftsTheLock() {
        User user = User.builder().build();
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 8, 0);
        user.registerFailedLogin(1, now.plusMinutes(5));

        user.clearLoginFailures();

        assertThat(user.isLockedAt(now)).isFalse();
        assertThat(user.hasLoginFailures()).isFalse();
    }

    /** El registro solo pide correo y contraseña: el resto se completa en el primer ingreso. */
    @Nested
    class CompleteProfile {

        @Test
        void registeredAccountMustCompleteItsProfile() {
            User user = User.builder().email(EMAIL).dataConsent(true).build();

            assertThat(user.pendingSteps()).containsExactly(COMPLETE_PROFILE);
        }

        @Test
        void blankNamesCountAsMissing() {
            User user = complete(Role.STUDENT).firstName(" ").lastName("").build();

            assertThat(user.pendingSteps()).containsExactly(COMPLETE_PROFILE);
        }

        @Test
        void studentsAndTeachersAlsoNeedTheirFaculty() {
            assertThat(complete(Role.STUDENT).faculty(null).build().pendingSteps())
                    .containsExactly(COMPLETE_PROFILE);
            assertThat(complete(Role.TEACHER).faculty(null).build().pendingSteps())
                    .containsExactly(COMPLETE_PROFILE);
        }

        @Test
        void rolesWithoutAffiliationOnlyNeedTheirName() {
            assertThat(complete(Role.JUDGE).faculty(null).build().pendingSteps())
                    .isEmpty();
            assertThat(complete(Role.MACONDOLAB).faculty(null).build().pendingSteps())
                    .isEmpty();
            assertThat(complete(Role.ADMIN).faculty(null).build().pendingSteps())
                    .isEmpty();
        }

        @Test
        void theProfileComesAfterPasswordAndConsent() {
            User user = User.builder()
                    .email(EMAIL)
                    .role(Role.STUDENT)
                    .mustChangePassword(true)
                    .build();

            assertThat(user.pendingSteps()).containsExactly(CHANGE_PASSWORD, DATA_CONSENT, COMPLETE_PROFILE);
        }
    }

    @Nested
    class FullName {

        @Test
        void joinsNamesAndSurnames() {
            assertThat(complete(Role.STUDENT).build().fullName()).isEqualTo("Ana Pérez");
        }

        @Test
        void untilTheProfileIsCompleteItNeverShowsAPieceOfTheEmail() {
            assertThat(User.builder().email(EMAIL).build().fullName()).isEqualTo("Sin nombre");
            assertThat(complete(Role.STUDENT).lastName(null).build().fullName()).isEqualTo("Sin nombre");
        }

        @Test
        void aMailToThePersonGreetsByNameOnlyIfThereIsOne() {
            assertThat(complete(Role.STUDENT).build().nameForGreeting()).isEqualTo("Ana Pérez");
            assertThat(User.builder().email(EMAIL).build().nameForGreeting()).isNull();
        }
    }
}
