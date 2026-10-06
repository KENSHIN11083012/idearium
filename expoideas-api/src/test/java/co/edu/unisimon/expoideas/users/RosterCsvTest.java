package co.edu.unisimon.expoideas.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unisimon.expoideas.users.RosterCsv.Row;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El CSV como lo exporta Excel: separadores, codificaciones, comillas y columnas en cualquier orden. */
class RosterCsvTest {

    @Test
    void readsSemicolonsWithBomAndColumnsInAnyOrder() {
        String csv = "﻿Rol;Apellidos;Correo electrónico;Nombres\n"
                + "Profesor;Mendoza;carlos@unisimon.edu.co;Carlos\n"
                + "\n"
                + "estudiante;Pérez;ana@unisimon.edu.co;Ana María\n";

        List<Row> rows = RosterCsv.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(rows)
                .containsExactly(
                        new Row(2, "carlos@unisimon.edu.co", "Profesor", "Carlos", "Mendoza"),
                        new Row(4, "ana@unisimon.edu.co", "estudiante", "Ana María", "Pérez"));
    }

    @Test
    void readsCommasQuotesAndTheWindowsEncoding() {
        String csv = "correo,rol,nombres\n" + "ana@unisimon.edu.co,estudiante,\"Pérez, Ana \"\"Anita\"\"\"\n";

        List<Row> rows = RosterCsv.parse(csv.getBytes(Charset.forName("windows-1252")));

        assertThat(rows).containsExactly(new Row(2, "ana@unisimon.edu.co", "estudiante", "Pérez, Ana \"Anita\"", null));
    }

    @Test
    void missingCellsAreNullNotEmptyText() {
        List<Row> rows = RosterCsv.parse(
                "correo;rol;nombres;apellidos\nana@unisimon.edu.co;;;\n".getBytes(StandardCharsets.UTF_8));

        assertThat(rows).containsExactly(new Row(2, "ana@unisimon.edu.co", null, null, null));
    }

    @Test
    void anEmptyFileOrOneWithoutTheColumnsIsRejected() {
        assertThatThrownBy(() -> RosterCsv.parse(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
        assertThatThrownBy(() -> RosterCsv.parse("nombre;apellido\nAna;Pérez\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("«correo» y «rol»");
    }
}
