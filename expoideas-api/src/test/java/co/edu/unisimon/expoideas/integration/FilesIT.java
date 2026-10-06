package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Foto de perfil: subida, descarga pública con caché, reemplazo, borrado y límites. */
class FilesIT extends IntegrationTest {

    private static final String PHOTO = "/api/v1/users/me/photo";
    private static final String PART = "file";

    @Test
    void photoIsUploadedServedAndCached() {
        String token = loginAs(Role.STUDENT);
        byte[] png = png(1_000);

        String id = putFile(PHOTO, token, PART, "yo.png", MediaType.IMAGE_PNG, png)
                .expect(200)
                .json("$.photoId");
        assertThat(get("/api/v1/users/me", token).<String>json("$.photoId")).isEqualTo(id);

        // Pública: se descarga sin sesión, idéntica, con caché larga y ETag.
        Response download = get("/api/v1/files/" + id, null).expect(200);
        assertThat(download.bytes()).isEqualTo(png);
        assertThat(download.headers().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(download.headers().getCacheControl()).contains("immutable");
        assertThat(download.headers().getFirst("Content-Security-Policy")).contains("sandbox");
        String etag = download.headers().getETag();
        assertThat(etag).isNotBlank();

        HttpHeaders conditional = new HttpHeaders();
        conditional.setIfNoneMatch(etag);
        get("/api/v1/files/" + id, null, conditional).expect(304);

        assertThat(storedFileCount(id)).isEqualTo(1);
    }

    @Test
    void replacingPhotoDeletesThePreviousOne() {
        String token = loginAs(Role.STUDENT);
        String first = putFile(PHOTO, token, PART, "a.png", MediaType.IMAGE_PNG, png(100))
                .expect(200)
                .json("$.photoId");
        String second = putFile(PHOTO, token, PART, "b.png", MediaType.IMAGE_PNG, png(200))
                .expect(200)
                .json("$.photoId");

        assertThat(second).isNotEqualTo(first);
        get("/api/v1/files/" + first, null).expect(404);
        get("/api/v1/files/" + second, null).expect(200);
        assertThat(storedFileCount(first)).isZero();
    }

    @Test
    void deletingPhotoRemovesIt() {
        String token = loginAs(Role.STUDENT);
        String id = putFile(PHOTO, token, PART, "a.png", MediaType.IMAGE_PNG, png(100))
                .expect(200)
                .json("$.photoId");

        delete(PHOTO, token).expect(204);

        get("/api/v1/files/" + id, null).expect(404);
        assertThat(get("/api/v1/users/me", token).<String>json("$.photoId")).isNull();
        assertThat(storedFileCount(id)).isZero();
    }

    @Test
    void deletingAccountRemovesItsFiles() {
        String email = createAccount(Role.STUDENT);
        String token = login(email, PASSWORD);
        String id = putFile(PHOTO, token, PART, "a.png", MediaType.IMAGE_PNG, png(100))
                .expect(200)
                .json("$.photoId");
        int userId = userRepository.findByEmail(email).orElseThrow().getId();

        delete("/api/v1/admin/users/" + userId, loginAs(Role.ADMIN)).expect(204);

        get("/api/v1/files/" + id, null).expect(404);
        assertThat(storedFileCount(id)).isZero();
    }

    @Test
    void contentIsValidatedBySignatureNotByDeclaredType() {
        String token = loginAs(Role.STUDENT);
        byte[] text = "no soy una imagen".getBytes(StandardCharsets.UTF_8);

        Response rejected = putFile(PHOTO, token, PART, "falsa.png", MediaType.IMAGE_PNG, text)
                .expect(400);
        assertThat(rejected.body()).contains("JPG, PNG o WEBP");
        putFile(PHOTO, token, PART, "vacia.png", MediaType.IMAGE_PNG, new byte[0])
                .expect(400);
    }

    @Test
    void filesOverFiveMegabytesAre413() {
        String token = loginAs(Role.STUDENT);
        putFile(PHOTO, token, PART, "grande.png", MediaType.IMAGE_PNG, png(5_500_000))
                .expect(413);
    }

    @Test
    void photoUploadRequiresSession() {
        get("/api/v1/files/00000000-0000-0000-0000-000000000000", null).expect(404);
        delete(PHOTO, null).expect(401);
    }

    /** Contenido con la firma de PNG, del tamaño pedido. */
    private static byte[] png(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 7);
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, content, 0, Math.min(signature.length, size));
        return content;
    }

    /** Archivos en disco cuyo nombre empieza por ese identificador. */
    private static long storedFileCount(String id) {
        try (Stream<Path> files = Files.walk(FILES_DIR)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(id))
                    .count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
