package co.edu.unisimon.expoideas.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.common.TimeConfig;
import co.edu.unisimon.expoideas.security.UserPrincipal;
import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final String EMAIL = "ana@unisimon.edu.co";

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileStorage storage;

    /**
     * Sin permisos de otros módulos: aquí solo se prueban el propietario y la
     * gestión. La regla de los entregables tiene sus pruebas de integración.
     */
    private final List<PrivateFileAccessRule> accessRules = List.of();

    /** Las 03:15 UTC del 1 de octubre: en Colombia todavía es septiembre. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T03:15:00Z"), TimeConfig.ZONE);

    private FileService service;

    private final User ana = TestData.user(1, EMAIL, Role.STUDENT);

    @BeforeEach
    void startTransaction() {
        service = new FileService(fileRepository, storage, clock, accessRules);
        // Los métodos exigen transacción: aquí se simula para poder terminarla a mano.
        TransactionSynchronizationManager.initSynchronization();
        lenient().when(fileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    private static void endTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }

    private static MockMultipartFile upload(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/png", content);
    }

    // ── Guardar ─────────────────────────────────────────────────────────────

    @Test
    void storesAnImageWithItsMetadata() {
        StoredFile file = service.store(
                upload("C:\\fotos\\mi foto.PNG", TestData.PNG), FileFormat.IMAGES, FileVisibility.PUBLIC, ana);

        assertThat(UUID.fromString(file.getUuid())).isNotNull();
        assertThat(file.getContentType()).isEqualTo("image/png");
        assertThat(file.getSizeBytes()).isEqualTo(TestData.PNG.length);
        assertThat(file.getSha256()).hasSize(64);
        assertThat(file.getOriginalName()).isEqualTo("mi foto.png");
        // La carpeta es el mes en Colombia, no el del servidor.
        assertThat(file.getStoragePath()).isEqualTo("2026/09/" + file.getUuid() + ".png");
        assertThat(file.getOwner()).isSameAs(ana);
        verify(storage).save(file.getStoragePath(), TestData.PNG);
    }

    @Test
    void theContentDecidesTheTypeNotTheBrowser() {
        MockMultipartFile disguised = upload("foto.png", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> service.store(disguised, FileFormat.IMAGES, FileVisibility.PUBLIC, ana))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        ex -> assertThat(ex.getFields())
                                .containsEntry("file", "Formato no permitido. Usa un archivo JPG, PNG o WEBP."));
        verify(storage, never()).save(anyString(), any());
    }

    @Test
    void aPdfIsRejectedWhereOnlyImagesAreAllowed() {
        MockMultipartFile pdf = new MockMultipartFile("file", "poster.pdf", "application/pdf", TestData.PDF);

        assertThatThrownBy(() -> service.store(pdf, FileFormat.IMAGES, FileVisibility.PUBLIC, ana))
                .isInstanceOf(InvalidFieldsException.class);
        assertThat(service.store(pdf, EnumSet.of(FileFormat.PDF), FileVisibility.PRIVATE, ana)
                        .getContentType())
                .isEqualTo("application/pdf");
    }

    @Test
    void rejectsEmptyFilesAndFilesOverFiveMegabytes() {
        assertThatThrownBy(() ->
                        service.store(upload("vacio.png", new byte[0]), FileFormat.IMAGES, FileVisibility.PUBLIC, ana))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        ex -> assertThat(ex.getFields()).containsEntry("file", "El archivo está vacío."));

        byte[] big = new byte[FileService.MAX_SIZE_BYTES + 1];
        System.arraycopy(TestData.PNG, 0, big, 0, TestData.PNG.length);
        assertThatThrownBy(
                        () -> service.store(upload("grande.png", big), FileFormat.IMAGES, FileVisibility.PUBLIC, ana))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        ex -> assertThat(ex.getFields())
                                .containsEntry("file", "El archivo supera el tamaño máximo permitido de 5 MB."));
    }

    @Test
    void rolledBackTransactionDeletesTheWrittenContent() {
        StoredFile file = service.store(upload("a.png", TestData.PNG), FileFormat.IMAGES, FileVisibility.PUBLIC, ana);

        endTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(file.getStoragePath());
    }

    @Test
    void committedTransactionKeepsTheContent() {
        service.store(upload("a.png", TestData.PNG), FileFormat.IMAGES, FileVisibility.PUBLIC, ana);

        endTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void storesTheSanitizedName() {
        service.store(upload("../foto.png", TestData.PNG), FileFormat.IMAGES, FileVisibility.PUBLIC, ana);

        ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
        verify(fileRepository).save(saved.capture());
        assertThat(saved.getValue().getOriginalName()).isEqualTo("foto.png");
    }

    // ── Borrar ──────────────────────────────────────────────────────────────

    @Test
    void contentIsDeletedOnlyWhenTheRecordReallyWas() {
        StoredFile file = StoredFile.builder().storagePath("2026/09/x.png").build();

        service.delete(file);
        verify(fileRepository).delete(file);
        endTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage, never()).delete(anyString());

        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.initSynchronization();
        service.delete(file);
        endTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage).delete("2026/09/x.png");
    }

    // ── Descargar ───────────────────────────────────────────────────────────

    private StoredFile registered(FileVisibility visibility) {
        StoredFile file = StoredFile.builder()
                .uuid(UUID.randomUUID().toString())
                .originalName("poster.pdf")
                .contentType("application/pdf")
                .sizeBytes(9)
                .sha256("abc")
                .storagePath("2026/09/p.pdf")
                .visibility(visibility)
                .owner(ana)
                .build();
        when(fileRepository.findByUuid(file.getUuid())).thenReturn(Optional.of(file));
        lenient().when(storage.open("2026/09/p.pdf")).thenReturn(new ByteArrayResource(TestData.PDF));
        return file;
    }

    private static Authentication session(String email, Role role) {
        UserPrincipal principal = new UserPrincipal(TestData.user(9, email, role));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Test
    void anyoneDownloadsAPublicFile() {
        StoredFile file = registered(FileVisibility.PUBLIC);

        FileContent content = service.open(UUID.fromString(file.getUuid()), null);

        assertThat(content.isPublic()).isTrue();
        assertThat(content.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void onlyTheOwnerAndManagementSeeAPrivateFile() {
        UUID id = UUID.fromString(registered(FileVisibility.PRIVATE).getUuid());

        assertThat(service.open(id, session("ANA@unisimon.edu.co", Role.STUDENT))
                        .isPublic())
                .isFalse();
        assertThat(service.open(id, session("carla@unisimon.edu.co", Role.MACONDOLAB)))
                .isNotNull();
        assertThat(service.open(id, session("luis@unisimon.edu.co", Role.ADMIN)))
                .isNotNull();
        assertThatThrownBy(() -> service.open(id, session("eva@unisimon.edu.co", Role.STUDENT)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.open(id, session("marta@empresa.com", Role.JUDGE)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.open(id, null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.open(
                        id,
                        new AnonymousAuthenticationToken(
                                "clave", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void unknownIdentifierIsNotFound() {
        assertThatThrownBy(() -> service.open(UUID.randomUUID(), null)).isInstanceOf(NoSuchElementException.class);
    }

    // ── Nombres ─────────────────────────────────────────────────────────────

    @Test
    void originalNameIsSanitizedAndGetsTheRealExtension() {
        assertThat(FileService.safeName("../../etc/passwd.png", FileFormat.PNG)).isEqualTo("passwd.png");
        assertThat(FileService.safeName("foto.exe", FileFormat.JPEG)).isEqualTo("foto.jpg");
        assertThat(FileService.safeName(".htaccess", FileFormat.PNG)).isEqualTo("archivo.png");
        assertThat(FileService.safeName(null, FileFormat.PDF)).isEqualTo("archivo.pdf");
        assertThat(FileService.safeName("mal\"nombre\r\n.pdf", FileFormat.PDF)).isEqualTo("malnombre.pdf");
        assertThat(FileService.safeName("x".repeat(300) + ".pdf", FileFormat.PDF))
                .hasSize(204);
    }
}
