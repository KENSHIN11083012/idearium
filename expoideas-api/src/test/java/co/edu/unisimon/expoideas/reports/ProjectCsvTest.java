package co.edu.unisimon.expoideas.reports;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.evaluations.GradeScale;
import co.edu.unisimon.expoideas.projects.ProjectResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El CSV que se abre en una hoja de cálculo: cabeceras, separadores y comillas. */
class ProjectCsvTest {

    @Test
    void writesAHeaderAndARowPerProject() {
        String csv = ProjectCsv.of(List.of(project("BioSensor IoT", "Agroindustria y alimentos")));
        String[] lines = csv.split("\n");

        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith(ProjectCsv.BOM + "Edición;Cátedra;Proyecto");
        assertThat(lines[1])
                .isEqualTo("Expoideas 2026-2;INNPRENDE I · Despegue;BioSensor IoT;Agroindustria y alimentos;"
                        + "Prototipo digital;Carlos Mendoza;Ana Pérez;3;2;2;1;4.3;2 de 3;Aprobado;2026-11-04");
    }

    @Test
    void aValueWithASeparatorOrQuotesDoesNotBreakTheColumns() {
        String csv = ProjectCsv.of(List.of(project("Riego; inteligente", "Moda y \"textil\"")));
        String row = csv.split("\n")[1];

        assertThat(row).contains("\"Riego; inteligente\"").contains("\"Moda y \"\"textil\"\"\"");
        // Las comillas protegen el punto y coma: la fila sigue teniendo 15 columnas.
        assertThat(row.replaceAll("\"[^\"]*(\"\"[^\"]*)*\"", "X").split(";", -1))
                .hasSize(15);
    }

    @Test
    void aValueThatWouldStartAFormulaIsWrittenAsText() {
        String csv = ProjectCsv.of(List.of(
                project("=HYPERLINK(\"http://malo.example\";\"Ver\")", "+57 300"),
                project("@SUM(1)", "-1+1"),
                project("\tcmd", "Salud")));
        String[] rows = csv.split("\n");

        // El título y los nombres los escribe el estudiante: nunca deben abrirse como fórmula.
        assertThat(rows[1]).contains(";\"'=HYPERLINK(\"\"http://malo.example\"\";\"\"Ver\"\")\";'+57 300;");
        assertThat(rows[2]).contains(";'@SUM(1);'-1+1;");
        assertThat(rows[3]).contains(";'\tcmd;Salud;");
    }

    @Test
    void aNumberOrADashInsideTheTextStaysAsItIs() {
        String row = ProjectCsv.of(List.of(project("Agro-Tech 4.0", "I+D"))).split("\n")[1];

        assertThat(row).contains(";Agro-Tech 4.0;I+D;").contains(";4.3;2 de 3;");
    }

    @Test
    void anEmptyListIsJustTheHeader() {
        assertThat(ProjectCsv.of(List.of()).split("\n")).hasSize(1);
    }

    private static ProjectSummaryResponse project(String title, String sector) {
        return new ProjectSummaryResponse(
                1,
                1,
                "Expoideas 2026-2",
                Track.INNPRENDE_I,
                title,
                sector,
                "Prototipo digital",
                "Carlos Mendoza",
                "Ana Pérez",
                3,
                2,
                2,
                1,
                new BigDecimal("4.3"),
                GradeScale.GOOD,
                3,
                2,
                ProjectResult.APPROVED,
                LocalDateTime.of(2026, 11, 4, 9, 30));
    }
}
