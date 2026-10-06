package co.edu.unisimon.expoideas.security;

import static co.edu.unisimon.expoideas.users.OnboardingStep.CHANGE_PASSWORD;
import static co.edu.unisimon.expoideas.users.OnboardingStep.DATA_CONSENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class DatabaseUserDetailsServiceTest {

    private static final String EMAIL = "marta@empresa.com";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository);

    @Test
    void principalCarriesRoleAndPendingStepsFromTheDatabase() {
        User newJudge = TestData.user(7, EMAIL, Role.JUDGE);
        newJudge.setMustChangePassword(true);
        newJudge.setDataConsent(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(newJudge));

        UserPrincipal principal = service.loadUserByUsername(EMAIL);

        assertThat(principal.getUsername()).isEqualTo(EMAIL);
        assertThat(principal.getRole()).isEqualTo(Role.JUDGE);
        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_JUDGE");
        assertThat(principal.getPendingSteps()).containsExactly(CHANGE_PASSWORD, DATA_CONSENT);
    }

    @Test
    void unknownEmailIsNotFound() {
        assertThatThrownBy(() -> service.loadUserByUsername("nadie@unisimon.edu.co"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
