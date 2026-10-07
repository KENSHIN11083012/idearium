package co.edu.unisimon.expoideas.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.support.TestData;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El archivo con los datos propios: la cuenta y lo que aporta cada módulo. */
@ExtendWith(MockitoExtension.class)
class PersonalDataServiceTest {

    private static final String EMAIL = "ana@unisimon.edu.co";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 30);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    private User ana;

    @BeforeEach
    void setUp() {
        ana = TestData.user(3, EMAIL, Role.STUDENT);
    }

    @Test
    void theFileHasTheAccountAndWhatEachModuleKeeps() {
        when(userRepository.findWithProfileByEmail(EMAIL)).thenReturn(Optional.of(ana));
        PersonalDataSource teams = user -> Map.of("teams", List.of(Map.of("project", "BioSensor")));
        PersonalDataSource files = user -> {
            Map<String, List<?>> sections = new LinkedHashMap<>();
            sections.put("files", List.of());
            sections.put("deliverables", List.of("poster.pdf"));
            return sections;
        };

        Map<String, Object> export =
                new PersonalDataService(userRepository, List.of(teams, files), CLOCK).export(EMAIL);

        // En orden: cuándo se generó, la cuenta y después cada módulo. Una sección vacía también va.
        assertThat(export.keySet()).containsExactly("generatedAt", "account", "teams", "files", "deliverables");
        assertThat(export.get("generatedAt")).isEqualTo(NOW);
        assertThat(export.get("files")).isEqualTo(List.of());
        PersonalDataService.Account account = (PersonalDataService.Account) export.get("account");
        assertThat(account.email()).isEqualTo(EMAIL);
        assertThat(account.firstName()).isEqualTo("Nombre");
        assertThat(account.role()).isEqualTo("Estudiante");
        assertThat(account.campus()).isEqualTo("Barranquilla");
        assertThat(account.faculty()).isEqualTo("Ingeniería");
        assertThat(account.dataConsent()).isTrue();
        assertThat(account.suspended()).isFalse();
        assertThat(account.hasPhoto()).isTrue();
    }

    @Test
    void theConsentSaysWhichTextWasAccepted() {
        User pedro = User.builder()
                .id(9)
                .email("pedro@unisimon.edu.co")
                .passwordHash("hash")
                .build();
        pedro.giveDataConsent(NOW.minusDays(2), DataConsent.VERSION);
        when(userRepository.findWithProfileByEmail(pedro.getEmail())).thenReturn(Optional.of(pedro));

        PersonalDataService.Account account =
                (PersonalDataService.Account) new PersonalDataService(userRepository, List.of(), CLOCK)
                        .export(pedro.getEmail())
                        .get("account");

        assertThat(account.dataConsentAt()).isEqualTo(NOW.minusDays(2));
        assertThat(account.dataConsentVersion()).isEqualTo("1");
        assertThat(account.firstName()).isNull();
    }

    @Test
    void twoModulesCannotWriteTheSameSection() {
        when(userRepository.findWithProfileByEmail(EMAIL)).thenReturn(Optional.of(ana));
        PersonalDataSource one = user -> Map.of("teams", List.of());
        PersonalDataSource other = user -> Map.of("teams", List.of("otra cosa"));

        assertThatThrownBy(() -> new PersonalDataService(userRepository, List.of(one, other), CLOCK).export(EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("teams");
    }

    @Test
    void anUnknownAccountHasNothingToExport() {
        when(userRepository.findWithProfileByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PersonalDataService(userRepository, List.of(), CLOCK).export(EMAIL))
                .isInstanceOf(NoSuchElementException.class);
    }
}
