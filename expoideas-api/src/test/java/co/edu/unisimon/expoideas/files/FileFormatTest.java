package co.edu.unisimon.expoideas.files;

import static co.edu.unisimon.expoideas.support.TestData.ascii;
import static co.edu.unisimon.expoideas.support.TestData.concat;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.support.TestData;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class FileFormatTest {

    @Test
    void recognizesAcceptedFormatsByTheirSignature() {
        assertThat(FileFormat.detect(TestData.PNG)).contains(FileFormat.PNG);
        assertThat(FileFormat.detect(TestData.JPEG)).contains(FileFormat.JPEG);
        assertThat(FileFormat.detect(TestData.WEBP)).contains(FileFormat.WEBP);
        assertThat(FileFormat.detect(TestData.PDF)).contains(FileFormat.PDF);
    }

    @Test
    void recognizesOfficeTemplatesByTheirZipEntryNotByAnyZip() {
        assertThat(FileFormat.detect(TestData.DOCX)).contains(FileFormat.DOCX);
        assertThat(FileFormat.detect(TestData.PPTX)).contains(FileFormat.PPTX);
        // Un ZIP cualquiera (o un .docx que no lo es) no pasa.
        assertThat(FileFormat.detect(TestData.ZIP)).isEmpty();
        // Y la entrada sola, sin la firma ZIP, tampoco.
        assertThat(FileFormat.detect(ascii("word/document.xml"))).isEmpty();
    }

    @Test
    void doesNotTrustExtensionsNorAcceptDangerousFormats() {
        assertThat(FileFormat.detect(
                        ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>")))
                .isEmpty();
        assertThat(FileFormat.detect(ascii("<!doctype html><script>alert(1)</script>")))
                .isEmpty();
        assertThat(FileFormat.detect(concat(ascii("MZ"), new byte[] {(byte) 0x90, 0}, ascii("ejecutable de Windows"))))
                .isEmpty();
        assertThat(FileFormat.detect(ascii("GIF89a"))).isEmpty();
        assertThat(FileFormat.detect(ascii("texto plano renombrado a foto.png")))
                .isEmpty();
        // Un WAV también es RIFF: no basta la primera firma.
        assertThat(FileFormat.detect(concat(ascii("RIFF"), new byte[] {36, 0, 0, 0}, ascii("WAVEfmt "))))
                .isEmpty();
    }

    @Test
    void contentShorterThanTheSignatureIsNoFormat() {
        assertThat(FileFormat.detect(new byte[0])).isEmpty();
        assertThat(FileFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(FileFormat.detect(ascii("RIFF1234WEB"))).isEmpty();
    }

    @Test
    void describesFormatsForErrorMessages() {
        assertThat(FileFormat.describe(FileFormat.IMAGES)).isEqualTo("JPG, PNG o WEBP");
        assertThat(FileFormat.describe(EnumSet.of(FileFormat.PDF))).isEqualTo("PDF");
        assertThat(FileFormat.describe(EnumSet.of(FileFormat.PDF, FileFormat.PNG)))
                .isEqualTo("PNG o PDF");
        assertThat(FileFormat.describe(FileFormat.TEMPLATES)).isEqualTo("PDF, DOCX o PPTX");
    }
}
