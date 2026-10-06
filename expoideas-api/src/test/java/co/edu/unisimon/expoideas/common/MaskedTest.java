package co.edu.unisimon.expoideas.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskedTest {

    @Test
    void anEmailKeepsItsFirstLetterAndItsDomain() {
        assertThat(Masked.email("ana.perez@unisimon.edu.co")).isEqualTo("a***@unisimon.edu.co");
        assertThat(Masked.email("j@empresa.com")).isEqualTo("j***@empresa.com");
    }

    @Test
    void whatIsNotAnEmailIsNotWrittenAtAll() {
        assertThat(Masked.email("sin-arroba")).isEqualTo("***");
        assertThat(Masked.email("@unisimon.edu.co")).isEqualTo("***");
        assertThat(Masked.email(null)).isEqualTo("(sin correo)");
        assertThat(Masked.email(" ")).isEqualTo("(sin correo)");
    }
}
