package co.edu.unisimon.expoideas.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.catalogs.AcademicProgram;
import co.edu.unisimon.expoideas.catalogs.AcademicProgramRepository;
import co.edu.unisimon.expoideas.catalogs.Campus;
import co.edu.unisimon.expoideas.catalogs.CampusRepository;
import co.edu.unisimon.expoideas.catalogs.CatalogLookup;
import co.edu.unisimon.expoideas.catalogs.Faculty;
import co.edu.unisimon.expoideas.catalogs.FacultyRepository;
import co.edu.unisimon.expoideas.catalogs.PrototypeTypeRepository;
import co.edu.unisimon.expoideas.catalogs.SectorRepository;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.common.TimeConfig;
import co.edu.unisimon.expoideas.files.FileFormat;
import co.edu.unisimon.expoideas.files.FileService;
import co.edu.unisimon.expoideas.files.FileVisibility;
import co.edu.unisimon.expoideas.files.StoredFile;
import co.edu.unisimon.expoideas.support.TestData;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    private static final String EMAIL = "ana@unisimon.edu.co";

    @Mock
    private UserRepository userRepository;

    @Mock
    private CampusRepository campusRepository;

    @Mock
    private FacultyRepository facultyRepository;

    @Mock
    private AcademicProgramRepository academicProgramRepository;

    @Mock
    private SectorRepository sectorRepository;

    @Mock
    private PrototypeTypeRepository prototypeTypeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FileService fileService;

    @Mock
    private RosterRepository rosterRepository;

    @Mock
    private AccountLinkService accountLinks;

    /** Las 03:15 UTC del 1 de octubre: en Colombia todavía es 30 de septiembre, 22:15. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T03:15:00Z"), TimeConfig.ZONE);

    private UserAccountService service;

    private final Campus barranquilla = new Campus(1, "Barranquilla");
    private final Faculty engineering = new Faculty(10, "Ingeniería");
    private final Faculty law = new Faculty(20, "Derecho");
    private final AcademicProgram systems = new AcademicProgram(100, "Ingeniería de Sistemas", engineering);

    private User ana;

    @BeforeEach
    void setUp() {
        CatalogLookup lookup = new CatalogLookup(
                campusRepository,
                facultyRepository,
                academicProgramRepository,
                sectorRepository,
                prototypeTypeRepository);
        service = new UserAccountService(
                userRepository,
                rosterRepository,
                new AffiliationResolver(lookup),
                new PasswordUpdater(passwordEncoder),
                fileService,
                CLOCK,
                user -> "token-de-" + user.getTokenVersion(),
                accountLinks);
        lenient().when(rosterRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        lenient().when(campusRepository.findById(1)).thenReturn(Optional.of(barranquilla));
        lenient().when(facultyRepository.findById(10)).thenReturn(Optional.of(engineering));
        lenient().when(facultyRepository.findById(20)).thenReturn(Optional.of(law));
        lenient().when(academicProgramRepository.findById(100)).thenReturn(Optional.of(systems));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hash-nuevo");

        ana = TestData.user(1, EMAIL, Role.STUDENT);
        lenient().when(userRepository.findWithProfileByEmail(EMAIL)).thenReturn(Optional.of(ana));
    }

    @Nested
    class Registration {

        @Test
        void createsAStudentWithConsentAndTheProfilePending() {
            UserResponse created = service.register(registration(" ana@unisimon.edu.co "));

            assertThat(created.email()).isEqualTo(EMAIL);
            assertThat(created.role()).isEqualTo(Role.STUDENT);
            assertThat(created.firstName()).isNull();
            assertThat(created.lastName()).isNull();
            assertThat(created.campusId()).isNull();
            assertThat(created.facultyId()).isNull();
            assertThat(created.pendingSteps()).containsExactly(OnboardingStep.COMPLETE_PROFILE);
            verifyNoInteractions(campusRepository, facultyRepository, academicProgramRepository);
        }

        @Test
        void whoeverIsOnTheRosterAsATeacherStartsAsAStudentWithTheRolePending() {
            RosterEntry entry = RosterEntry.of(EMAIL);
            entry.setRole(Role.TEACHER);
            entry.setFirstName("Carlos");
            entry.setLastName("Mendoza");
            when(rosterRepository.findByEmail(EMAIL)).thenReturn(Optional.of(entry));

            UserResponse created = service.register(registration("Ana@unisimon.edu.co"));

            // Nadie comprobó que el correo sea suyo: el rol de profesor lo confirma la gestión.
            assertThat(created.role()).isEqualTo(Role.STUDENT);
            assertThat(created.pendingRole()).isEqualTo(Role.TEACHER);
            assertThat(created.firstName()).isEqualTo("Carlos");
            assertThat(created.lastName()).isEqualTo("Mendoza");
            assertThat(created.pendingSteps()).containsExactly(OnboardingStep.COMPLETE_PROFILE);
        }

        @Test
        void whenThePlatformCanSendMailTheAccountMustVerifyItsEmailFirst() {
            when(accountLinks.canSendLinks()).thenReturn(true);

            UserResponse created = service.register(registration(EMAIL));

            assertThat(created.pendingSteps())
                    .containsExactly(OnboardingStep.VERIFY_EMAIL, OnboardingStep.COMPLETE_PROFILE);
            verify(accountLinks).sendVerification(any(User.class));
        }

        @Test
        void withoutAMailServerThereIsNothingToVerify() {
            UserResponse created = service.register(registration(EMAIL));

            assertThat(created.pendingSteps()).containsExactly(OnboardingStep.COMPLETE_PROFILE);
            verify(accountLinks, never()).sendVerification(any());
        }

        @Test
        void whoeverIsOnTheRosterAsAStudentHasNothingPending() {
            RosterEntry entry = RosterEntry.of(EMAIL);
            entry.setRole(Role.STUDENT);
            when(rosterRepository.findByEmail(EMAIL)).thenReturn(Optional.of(entry));

            UserResponse created = service.register(registration(EMAIL));

            assertThat(created.role()).isEqualTo(Role.STUDENT);
            assertThat(created.pendingRole()).isNull();
        }

        @Test
        void takenEmailIsAConflict() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> service.register(registration(EMAIL))).isInstanceOf(ConflictException.class);
            verify(userRepository, never()).save(any());
        }

        private static RegistrationRequest registration(String email) {
            return new RegistrationRequest(email, TestData.PASSWORD, true);
        }
    }

    @Nested
    class Profile {

        @Test
        void canRemoveTheProgram() {
            ana.setFaculty(engineering);
            ana.setAcademicProgram(systems);

            UserResponse updated =
                    service.updateProfile(EMAIL, new ProfileUpdateRequest(" Ana ", "Pérez", 1, 20, null));

            assertThat(updated.firstName()).isEqualTo("Ana");
            assertThat(updated.faculty()).isEqualTo("Derecho");
            assertThat(updated.academicProgram()).isNull();
        }

        @Test
        void studentMustKeepCampusAndFaculty() {
            assertThatThrownBy(() ->
                            service.updateProfile(EMAIL, new ProfileUpdateRequest("Ana", "Pérez", null, null, null)))
                    .isInstanceOfSatisfying(
                            InvalidFieldsException.class,
                            ex -> assertThat(ex.getFields()).containsOnlyKeys("campusId", "facultyId"));
        }

        @Test
        void adminProfileIgnoresAffiliation() {
            // Una cuenta de gestión no lleva adscripción.
            ana.setRole(Role.ADMIN);
            ana.setCampus(null);
            ana.setFaculty(null);

            UserResponse updated = service.updateProfile(EMAIL, new ProfileUpdateRequest("Luis", "Gómez", 1, 10, 100));

            assertThat(updated.firstName()).isEqualTo("Luis");
            assertThat(updated.campusId()).isNull();
            assertThat(updated.facultyId()).isNull();
            verifyNoInteractions(campusRepository, facultyRepository, academicProgramRepository);
        }
    }

    @Nested
    class Photo {

        private final MockMultipartFile image = new MockMultipartFile("file", "yo.png", "image/png", TestData.PNG);

        @Test
        void firstPhotoIsStoredAsAPublicImage() {
            when(fileService.store(image, FileFormat.IMAGES, FileVisibility.PUBLIC, ana))
                    .thenReturn(file("nueva"));

            assertThat(service.updatePhoto(EMAIL, image).photoId()).isEqualTo("nueva");
            verify(fileService, never()).delete(any());
        }

        @Test
        void replacingThePhotoDeletesThePreviousOneAfterStoringTheNew() {
            StoredFile previous = file("anterior");
            ana.setPhoto(previous);
            when(fileService.store(any(), any(), any(), any())).thenReturn(file("nueva"));

            assertThat(service.updatePhoto(EMAIL, image).photoId()).isEqualTo("nueva");

            InOrder order = inOrder(fileService);
            order.verify(fileService).store(any(), any(), any(), any());
            order.verify(fileService).delete(previous);
        }

        @Test
        void deletingThePhotoRemovesIt() {
            StoredFile photo = file("actual");
            ana.setPhoto(photo);

            service.deletePhoto(EMAIL);

            assertThat(ana.getPhoto()).isNull();
            verify(fileService).delete(photo);
        }

        @Test
        void deletingWithoutAPhotoDoesNothing() {
            service.deletePhoto(EMAIL);

            verify(fileService, never()).delete(any());
        }

        private static StoredFile file(String uuid) {
            return StoredFile.builder().uuid(uuid).storagePath(uuid + ".png").build();
        }
    }

    @Nested
    class Password {

        @Test
        void changingItClearsTheTemporaryFlag() {
            ana.setMustChangePassword(true);
            when(passwordEncoder.matches("Temporal#2026", "hash-actual")).thenReturn(true);

            service.changePassword(EMAIL, new PasswordChangeRequest("Temporal#2026", "Propia#2026", "Propia#2026"));

            assertThat(ana.isMustChangePassword()).isFalse();
            assertThat(ana.getPasswordHash()).isEqualTo("hash-nuevo");
        }

        @Test
        void changingItClosesTheOpenSessionsAndReturnsATokenForThisOne() {
            when(passwordEncoder.matches("Temporal#2026", "hash-actual")).thenReturn(true);
            int before = ana.getTokenVersion();

            String token = service.changePassword(
                    EMAIL, new PasswordChangeRequest("Temporal#2026", "Propia#2026", "Propia#2026"));

            // Los tokens de antes llevan la versión anterior; el que se devuelve, la nueva.
            assertThat(ana.getTokenVersion()).isEqualTo(before + 1);
            assertThat(token).isEqualTo("token-de-" + (before + 1));
        }

        @Test
        void wrongCurrentPasswordIsAFieldError() {
            assertThatThrownBy(() -> service.changePassword(
                            EMAIL, new PasswordChangeRequest("Mala#2026", "Nueva#2026", "Nueva#2026")))
                    .isInstanceOfSatisfying(
                            InvalidFieldsException.class,
                            ex -> assertThat(ex.getFields())
                                    .containsEntry("currentPassword", "La contraseña actual es incorrecta."));
            assertThat(ana.getPasswordHash()).isEqualTo("hash-actual");
        }

        @Test
        void confirmationMustMatch() {
            when(passwordEncoder.matches("Vieja#2026", "hash-actual")).thenReturn(true);

            assertThatThrownBy(() -> service.changePassword(
                            EMAIL, new PasswordChangeRequest("Vieja#2026", "Nueva#2026", "Otra#2026")))
                    .isInstanceOfSatisfying(
                            InvalidFieldsException.class,
                            ex -> assertThat(ex.getFields()).containsOnlyKeys("confirmPassword"));
        }

        @Test
        void newPasswordMustDifferFromTheCurrentOne() {
            when(passwordEncoder.matches("Igual#2026", "hash-actual")).thenReturn(true);

            assertThatThrownBy(() -> service.changePassword(
                            EMAIL, new PasswordChangeRequest("Igual#2026", "Igual#2026", "Igual#2026")))
                    .isInstanceOfSatisfying(
                            InvalidFieldsException.class,
                            ex -> assertThat(ex.getFields()).containsOnlyKeys("newPassword"));
        }
    }

    @Nested
    class DataConsent {

        @Test
        void isRecordedWithItsDate() {
            ana.setDataConsent(false);

            service.giveDataConsent(EMAIL);

            assertThat(ana.isDataConsent()).isTrue();
            assertThat(ana.getDataConsentAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 22, 15));
        }

        @Test
        void givingItAgainKeepsTheOriginalDate() {
            LocalDateTime original = LocalDateTime.of(2026, 9, 1, 10, 0);
            ana.setDataConsentAt(original);

            service.giveDataConsent(EMAIL);

            assertThat(ana.getDataConsentAt()).isEqualTo(original);
        }
    }
}
