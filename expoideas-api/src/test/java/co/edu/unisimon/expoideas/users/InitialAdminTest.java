package co.edu.unisimon.expoideas.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.common.ExpoideasProperties.AdminSettings;
import co.edu.unisimon.expoideas.support.TestData;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminTest {

    private static final String EMAIL = "sistemas@unisimon.edu.co";
    private static final String PASSWORD = "Arranque#2026";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-de-arranque");
        lenient().when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            saved.setId(1);
            return saved;
        });
    }

    @Test
    void aNewInstallationGetsItsFirstAdminFromTheEnvironment() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        start(EMAIL, PASSWORD);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User admin = saved.getValue();
        assertThat(admin.getEmail()).isEqualTo(EMAIL);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).isEqualTo("hash-de-arranque");
        // La contraseña del entorno es temporal, y los datos y la foto los pone la persona: el primer ingreso
        // pide las cuatro cosas.
        assertThat(admin.pendingSteps())
                .containsExactly(
                        OnboardingStep.CHANGE_PASSWORD,
                        OnboardingStep.DATA_CONSENT,
                        OnboardingStep.COMPLETE_PROFILE,
                        OnboardingStep.PROFILE_PHOTO);
        verify(events)
                .publishEvent(new AuditableAction(
                        Action.ADMIN_BOOTSTRAPPED, 1, EMAIL, "Cuenta creada (ADMIN_EMAIL, al arrancar)"));
    }

    @Test
    void onceThereIsAnAdminTheVariablesAreIgnored() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        start(EMAIL, PASSWORD);

        // Ni crean otra cuenta ni sirven para recuperar el acceso de la que ya existe.
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(events, passwordEncoder);
    }

    @Test
    void anAccountThatAlreadyRegisteredIsPromotedKeepingItsPassword() {
        User registered = TestData.user(7, EMAIL, Role.STUDENT);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(registered));

        start(EMAIL, PASSWORD);

        assertThat(registered.getRole()).isEqualTo(Role.ADMIN);
        assertThat(registered.getPasswordHash()).isEqualTo("hash-actual");
        assertThat(registered.isMustChangePassword()).isFalse();
        verify(userRepository, never()).save(any());
        verify(events)
                .publishEvent(new AuditableAction(
                        Action.ADMIN_BOOTSTRAPPED, 7, EMAIL, "Estudiante → Administrador (ADMIN_EMAIL, al arrancar)"));
    }

    @Test
    void withoutTheVariableNothingHappens() {
        start("", "");

        verifyNoInteractions(userRepository, events, passwordEncoder);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "corta#1", "sinnumeros#", "sinsimbolo2026"})
    void aMissingOrWeakPasswordDoesNotOpenThePlatform(String password) {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        start(EMAIL, password);

        // No se crea una cuenta de administración con una contraseña que la plataforma no aceptaría a nadie.
        verify(userRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    private void start(String email, String password) {
        ExpoideasProperties base = TestData.properties(Path.of("x"));
        ExpoideasProperties properties = new ExpoideasProperties(
                base.jwt(),
                base.cors(),
                base.files(),
                base.mail(),
                base.login(),
                new AdminSettings(email, password),
                base.appUrl());
        new InitialAdmin(properties, userRepository, new PasswordUpdater(passwordEncoder), events).run(null);
    }
}
