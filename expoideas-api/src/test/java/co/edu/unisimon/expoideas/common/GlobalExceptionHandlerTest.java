package co.edu.unisimon.expoideas.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

/** Lo que la persona lee cuando la base rechaza un cambio: no todo es «ya existe». */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "1062 | 409 | Ya existe un registro con esos datos",
                "1451 | 409 | No se puede eliminar ni cambiar: hay otros datos que dependen de este",
                "1452 | 409 | Uno de los datos a los que esto hace referencia ya no existe",
                "1406 | 400 | Uno de los datos es más largo de lo permitido",
                "1048 | 400 | Los datos enviados no cumplen las reglas de la plataforma",
                "3819 | 400 | Los datos enviados no cumplen las reglas de la plataforma",
            })
    void eachKindOfConstraintIsExplainedForWhatItIs(int mysqlCode, int status, String detail) {
        ProblemDetail problem = handler.handleDataIntegrity(violation("rechazado", mysqlCode));

        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getDetail()).startsWith(detail);
    }

    @Test
    void anUnknownRejectionDoesNotPretendToBeADuplicate() {
        ProblemDetail fromMysql = handler.handleDataIntegrity(violation("otra cosa", 9999));
        ProblemDetail withoutSqlCause =
                handler.handleDataIntegrity(new DataIntegrityViolationException("sin causa de la base"));

        for (ProblemDetail problem : new ProblemDetail[] {fromMysql, withoutSqlCause}) {
            assertThat(problem.getStatus()).isEqualTo(409);
            assertThat(problem.getDetail())
                    .isEqualTo("No se pudo guardar: los datos chocan con otros ya guardados")
                    .doesNotContain("Ya existe");
        }
    }

    @Test
    void theLogNamesTheConstraintButNeverTheValueThatCollided(CapturedOutput output) {
        handler.handleDataIntegrity(
                violation("Duplicate entry 'ana.perez@unisimon.edu.co' for key 'users.uk_users_email'", 1062));
        handler.handleDataIntegrity(violation(
                "Cannot delete or update a parent row: a foreign key constraint fails"
                        + " (`expoideas`.`projects`, CONSTRAINT `fk_projects_sector` FOREIGN KEY (`sector_id`))",
                1451));
        handler.handleDataIntegrity(violation("Check constraint 'chk_evaluation_scores_value' is violated.", 3819));

        assertThat(output.getAll())
                .contains("código 1062, restricción users.uk_users_email")
                .contains("código 1451, restricción fk_projects_sector")
                .contains("código 3819, restricción chk_evaluation_scores_value")
                .doesNotContain("ana.perez@unisimon.edu.co");
    }

    /** Como llega de verdad: la excepción de Spring envolviendo la de MySQL con su código. */
    private static DataIntegrityViolationException violation(String message, int mysqlCode) {
        SQLException cause = new SQLIntegrityConstraintViolationException(message, "23000", mysqlCode);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
