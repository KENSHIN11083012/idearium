package co.edu.unisimon.expoideas.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unisimon.expoideas.support.TestData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path root;

    private LocalFileStorage storage(Path directory) {
        return new LocalFileStorage(TestData.properties(directory));
    }

    @Test
    void savesOpensAndDeletes() throws IOException {
        LocalFileStorage storage = storage(root);

        storage.save("2026/09/abc.png", new byte[] {1, 2, 3});
        assertThat(Files.readAllBytes(root.resolve("2026/09/abc.png"))).containsExactly(1, 2, 3);
        assertThat(storage.open("2026/09/abc.png").getContentAsByteArray()).containsExactly(1, 2, 3);

        storage.delete("2026/09/abc.png");
        assertThat(root.resolve("2026/09/abc.png")).doesNotExist();
        assertThatNoException().isThrownBy(() -> storage.delete("2026/09/abc.png"));
    }

    @Test
    void createsTheRootDirectory() {
        Path missing = root.resolve("no/existe/aun");

        storage(missing);

        assertThat(missing).isDirectory();
    }

    @Test
    void neverOverwritesAFile() {
        LocalFileStorage storage = storage(root);
        storage.save("a.png", new byte[] {1});

        assertThatThrownBy(() -> storage.save("a.png", new byte[] {2})).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void neverLeavesTheRootDirectory() throws IOException {
        Files.writeString(root.resolveSibling("secreto.txt"), "no");
        LocalFileStorage storage = storage(root);

        assertThatThrownBy(() -> storage.open("../secreto.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.save("../../fuera.png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("../secreto.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingContentIsNotFound() {
        assertThatThrownBy(() -> storage(root).open("2026/09/perdido.png")).isInstanceOf(NoSuchElementException.class);
    }
}
