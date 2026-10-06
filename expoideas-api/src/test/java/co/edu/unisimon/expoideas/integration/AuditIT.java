package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.TimeConfig;
import co.edu.unisimon.expoideas.users.Role;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El rastro de auditoría contra MySQL: lo que deja cada acción, quién lo lee y
 * que se escribe en la misma transacción que la acción.
 */
class AuditIT extends IntegrationTest {

    private static final String AUDIT = "/api/v1/admin/audit";

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void whatManagementDoesToAnAccountLeavesATraceThatOnlyAnAdminReads() {
        String adminEmail = createAccount(Role.ADMIN);
        String admin = login(adminEmail, PASSWORD);
        String macondolabEmail = createAccount(Role.MACONDOLAB);
        String macondolab = login(macondolabEmail, PASSWORD);
        int macondolabId = idOf(macondolabEmail);
        String studentEmail = createAccount(Role.STUDENT);
        int studentId = idOf(studentEmail);
        String account = "/api/v1/admin/users/" + studentId;

        put(account, macondolab, Map.of("role", "TEACHER")).expect(200);
        post(
                        account + "/password-reset",
                        macondolab,
                        Map.of("newPassword", "Temporal#2027", "confirmPassword", "Temporal#2027"))
                .expect(204);
        post(account + "/suspension", macondolab, null).expect(200);
        post(account + "/reactivation", macondolab, null).expect(200);
        delete(account, admin).expect(204);

        // De lo más reciente a lo más antiguo.
        List<Map<String, Object>> trail = trailOf(admin, studentEmail);
        assertThat(trail)
                .extracting(row -> row.get("action"))
                .containsExactly(
                        "ACCOUNT_DELETED",
                        "ACCOUNT_REACTIVATED",
                        "ACCOUNT_SUSPENDED",
                        "PASSWORD_RESET",
                        "ROLE_CHANGED");

        Map<String, Object> roleChange = trail.getLast();
        assertThat(roleChange)
                .containsEntry("actionLabel", "Cambio de rol")
                .containsEntry("actorEmail", macondolabEmail)
                .containsEntry("actorId", macondolabId)
                .containsEntry("targetType", "ACCOUNT")
                .containsEntry("targetId", studentId)
                .containsEntry("detail", "Estudiante → Profesor");
        assertThat(roleChange.get("occurredAt")).asString().startsWith(today().toString());

        // La cuenta ya no existe, pero el rastro sigue diciendo quién era y quién la eliminó.
        assertThat(trail.getFirst())
                .containsEntry("actorEmail", adminEmail)
                .containsEntry("targetId", studentId)
                .containsEntry("detail", "Prueba STUDENT · Profesor");

        // El rastro recoge lo que hace MacondoLab: no lo lee MacondoLab.
        get(AUDIT, macondolab).expect(403);
        get(AUDIT, null).expect(401);

        // Tampoco se pierde al eliminar la cuenta de quien actuó: queda su correo.
        delete("/api/v1/admin/users/" + macondolabId, admin).expect(204);
        Map<String, Object> afterwards = trailOf(admin, studentEmail).getLast();
        assertThat(afterwards).containsEntry("actorEmail", macondolabEmail).containsEntry("actorId", null);
    }

    @Test
    void theRoleThatComesWithVerifyingTheEmailIsTracedWithoutAnActor() {
        String admin = loginAs(Role.ADMIN);
        String teacher = uniqueEmail("listado.rastro");
        postFile(
                        "/api/v1/admin/roster",
                        admin,
                        "file",
                        "listado.csv",
                        MediaType.parseMediaType("text/csv"),
                        ("correo;rol\n" + teacher + ";profesor\n").getBytes(StandardCharsets.UTF_8))
                .expect(200);
        register(teacher).expect(201);

        verifyEmail(teacher);

        assertThat(trailOf(admin, teacher))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("action", "ROLE_CHANGED")
                        .containsEntry("actorEmail", null)
                        .containsEntry("actorId", null)
                        .containsEntry("detail", "Estudiante → Profesor (del listado, al verificar el correo)"));
    }

    @Test
    void discardingTheRosterRoleIsTracedAsSuch() {
        String admin = loginAs(Role.ADMIN);
        String email = createAccount(Role.STUDENT);
        jdbc.update("UPDATE users SET pending_role = 'TEACHER' WHERE email = ?", email);

        put("/api/v1/admin/users/" + idOf(email), admin, Map.of("role", "STUDENT"))
                .expect(200);

        assertThat(trailOf(admin, email))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("action", "PENDING_ROLE_DISCARDED")
                        .containsEntry("detail", "Pedía Profesor; sigue como Estudiante"));
    }

    @Test
    void correctingAnEvaluationKeepsTheGradeItHad() {
        String admin = loginAs(Role.ADMIN);
        String macondolabEmail = createAccount(Role.MACONDOLAB);
        String macondolab = login(macondolabEmail, PASSWORD);
        String leader = loginAs(Role.STUDENT);
        String title = "Proyecto con rastro " + System.nanoTime();
        int projectId = createProject(leader, title);
        String martaEmail = createAccount(Role.JUDGE);
        String marta = login(martaEmail, PASSWORD);
        String jurors = "/api/v1/projects/" + projectId + "/jurors";
        String mine = "/api/v1/projects/" + projectId + "/evaluations/mine";
        Response rubric = get("/api/v1/rubrics/INNPRENDE_I", marta).expect(200);
        post(jurors, macondolab, Map.of("email", martaEmail)).expect(201);

        // Calificar por primera vez es lo normal: no deja rastro.
        int evaluationId = put(mine, marta, Map.of("scores", levels(rubric, 4, 4, 4, 4, 4, 4)))
                .expect(200)
                .json("$.id");
        assertThat(trailOf(admin, title)).isEmpty();

        // Corregir sí: con la nota que había y la que queda.
        put(mine, marta, Map.of("scores", levels(rubric, 3, 3, 3, 3, 3, 3))).expect(200);
        assertThat(trailOf(admin, title))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("action", "EVALUATION_EDITED")
                        .containsEntry("targetType", "EVALUATION")
                        .containsEntry("targetId", evaluationId)
                        .containsEntry("actorEmail", martaEmail)
                        .containsEntry("detail", "Jurado: Prueba JUDGE · Nota: 5.0 → 4.5"));

        // Con las notas ya a la vista del equipo, la corrección lo dice.
        String publication = "/api/v1/editions/" + openEdition() + "/tracks/INNPRENDE_I/grades-publication";
        String edition =
                get("/api/v1/editions/" + openEdition(), macondolab).expect(200).json("$.name");
        put(publication, macondolab, null).expect(200);
        put(mine, marta, Map.of("absent", true)).expect(200);
        assertThat(trailOf(admin, title).getFirst())
                .containsEntry(
                        "detail", "Jurado: Prueba JUDGE · Nota: 4.5 → no asistió (0.0) · con las notas ya publicadas");
        delete(publication, macondolab).expect(200);

        List<Map<String, Object>> publications = trailOf(admin, edition).stream()
                .filter(row -> macondolabEmail.equals(row.get("actorEmail")))
                .toList();
        assertThat(publications)
                .extracting(row -> row.get("action"))
                .containsExactly("GRADES_HIDDEN", "GRADES_PUBLISHED");
        assertThat(publications.getFirst())
                .containsEntry("targetType", "EDITION")
                .containsEntry("targetId", openEdition())
                .containsEntry("detail", "INNPRENDE I · Despegue");

        // Quitar al jurado cambia la nota del proyecto: queda quién era.
        delete(jurors + "/" + idOf(martaEmail), macondolab).expect(204);
        assertThat(trailOf(admin, title).getFirst())
                .containsEntry("action", "JUROR_REMOVED")
                .containsEntry("targetType", "PROJECT")
                .containsEntry("targetId", projectId)
                .containsEntry("actorEmail", macondolabEmail)
                .containsEntry("detail", "Jurado: Prueba JUDGE (" + martaEmail + ")");
    }

    @Test
    void whatATeamDeletesIsStillNamedInTheTrace() {
        String admin = loginAs(Role.ADMIN);
        String macondolab = loginAs(Role.MACONDOLAB);
        String leaderEmail = createAccount(Role.STUDENT);
        String leader = login(leaderEmail, PASSWORD);
        String title = "Proyecto que se borra " + System.nanoTime();
        int projectId = createProject(leader, title);
        String edition =
                get("/api/v1/editions/" + openEdition(), macondolab).expect(200).json("$.name");

        Map<String, Object> type = new HashMap<>();
        type.put("editionId", openEdition());
        type.put("track", "INNPRENDE_I");
        type.put("name", "Video del póster");
        type.put("description", "Entregable de prueba");
        type.put("kind", "LINK");
        type.put("required", false);
        type.put("maxFiles", 1);
        type.put("sortOrder", 9);
        int typeId =
                post("/api/v1/deliverable-types", macondolab, type).expect(201).json("$.id");
        String deliverables = "/api/v1/projects/" + projectId + "/deliverables";
        post(deliverables + "/links", leader, Map.of("deliverableTypeId", typeId, "url", "https://youtu.be/rastro"))
                .expect(200);
        int deliverableId =
                jdbc.queryForObject("SELECT id FROM deliverables WHERE project_id = ?", Integer.class, projectId);

        delete(deliverables + "/" + deliverableId, leader).expect(200);
        delete("/api/v1/projects/" + projectId, leader).expect(204);
        delete("/api/v1/deliverable-types/" + typeId, macondolab).expect(204);

        List<Map<String, Object>> trail = trailOf(admin, title);
        assertThat(trail)
                .extracting(row -> row.get("action"))
                .containsExactly("PROJECT_DELETED", "DELIVERABLE_DELETED");
        assertThat(trail.getFirst())
                .containsEntry("targetId", projectId)
                .containsEntry("actorEmail", leaderEmail)
                .containsEntry("detail", edition + " · INNPRENDE I · Despegue · integrantes: 1");
        assertThat(trail.getLast())
                .containsEntry("targetType", "DELIVERABLE")
                .containsEntry("targetId", deliverableId)
                .containsEntry("detail", "Video del póster: https://youtu.be/rastro");
    }

    @Test
    void theTraceIsWrittenInTheSameTransactionAsTheAction() {
        String admin = loginAs(Role.ADMIN);
        String reverted = "Acción revertida " + System.nanoTime();
        String kept = "Acción confirmada " + System.nanoTime();

        // Si la acción falla después de publicarse, su rastro se va con ella.
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    events.publishEvent(new AuditableAction(Action.PROJECT_DELETED, 1, reverted, null));
                    throw new IllegalStateException("la acción falla");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(trailOf(admin, reverted)).isEmpty();

        transactions.executeWithoutResult(
                status -> events.publishEvent(new AuditableAction(Action.PROJECT_DELETED, 1, kept, "x".repeat(600))));
        assertThat(trailOf(admin, kept))
                .singleElement()
                .satisfies(row ->
                        assertThat((String) row.get("detail")).hasSize(500).endsWith("…"));

        // Publicarla sin transacción es un error de quien programa: no se guarda a medias.
        assertThatThrownBy(() -> events.publishEvent(new AuditableAction(Action.PROJECT_DELETED, 1, kept, null)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theTraceIsFilteredByActionAndDayAndComesInPages() {
        String admin = loginAs(Role.ADMIN);
        String first = createAccount(Role.STUDENT);
        String second = createAccount(Role.STUDENT);
        post("/api/v1/admin/users/" + idOf(first) + "/suspension", admin, null).expect(200);
        post("/api/v1/admin/users/" + idOf(second) + "/suspension", admin, null).expect(200);
        post("/api/v1/admin/users/" + idOf(second) + "/reactivation", admin, null)
                .expect(200);
        LocalDate today = today();

        Response suspensions =
                get(AUDIT + "?action=ACCOUNT_SUSPENDED&size=1", admin).expect(200);
        assertThat(suspensions.<List<String>>json("$.items[*].targetLabel")).containsExactly(second);
        assertThat(suspensions.<Integer>json("$.page")).isZero();
        assertThat(suspensions.<Integer>json("$.size")).isEqualTo(1);
        assertThat(suspensions.<Integer>json("$.totalItems")).isGreaterThanOrEqualTo(2);
        assertThat(suspensions.<Integer>json("$.totalPages")).isEqualTo(suspensions.<Integer>json("$.totalItems"));
        Response nextPage =
                get(AUDIT + "?action=ACCOUNT_SUSPENDED&size=1&page=1", admin).expect(200);
        assertThat(nextPage.<List<String>>json("$.items[*].targetLabel")).containsExactly(first);

        // «Hasta» incluye ese día entero.
        Response ofToday = get(AUDIT + "?from=" + today + "&to=" + today + "&size=100", admin)
                .expect(200);
        assertThat(ofToday.<List<String>>json("$.items[*].targetLabel")).contains(first, second);
        Response ofTomorrow = get(AUDIT + "?from=" + today.plusDays(1), admin).expect(200);
        assertThat(ofTomorrow.<List<Object>>json("$.items")).isEmpty();
        assertThat(ofTomorrow.<Integer>json("$.totalItems")).isZero();

        // El tamaño de página tiene tope: nadie pide el rastro entero de una vez.
        assertThat(get(AUDIT + "?size=5000", admin).expect(200).<Integer>json("$.size"))
                .isEqualTo(200);

        get(AUDIT + "?from=" + today + "&to=" + today.minusDays(1), admin).expect(400);
        get(AUDIT + "?action=NO_EXISTE", admin).expect(400);
        get(AUDIT + "?from=ayer", admin).expect(400);
    }

    /** El día de hoy en Colombia, que es con el que se guarda el rastro. */
    private static LocalDate today() {
        return LocalDate.now(TimeConfig.ZONE);
    }

    /**
     * Las filas del rastro sobre algo con ese nombre, de la más reciente a la más
     * antigua. La base es de todas las pruebas: se mira solo lo de esta.
     */
    private List<Map<String, Object>> trailOf(String adminToken, String targetLabel) {
        List<Map<String, Object>> rows =
                get(AUDIT + "?size=100", adminToken).expect(200).json("$.items");
        return rows.stream()
                .filter(row -> targetLabel.equals(row.get("targetLabel")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private int createProject(String leaderToken, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", "INNPRENDE_I");
        body.put("title", title);
        body.put("summary", "Una propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(createAccount(Role.TEACHER)));
        return post("/api/v1/projects", leaderToken, body).expect(201).json("$.id");
    }

    /** Un nivel por criterio, en orden: la posición (desde 0) del nivel elegido en cada uno. */
    private static List<Map<String, Object>> levels(Response rubric, int... positions) {
        List<Map<String, Object>> scores = new ArrayList<>();
        for (int criterion = 0; criterion < positions.length; criterion++) {
            Map<String, Object> score = new HashMap<>();
            score.put("criterionId", rubric.<Integer>json("$.criteria[" + criterion + "].id"));
            score.put(
                    "levelId",
                    rubric.<Integer>json("$.criteria[" + criterion + "].levels[" + positions[criterion] + "].id"));
            scores.add(score);
        }
        return scores;
    }
}
