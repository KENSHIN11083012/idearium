package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Evaluación con rúbrica contra MySQL: las rúbricas que cargan las migraciones,
 * el recorrido de un jurado (calificar, corregir, marcar que no asistieron) y
 * la nota que ven el profesor del grupo y la gestión.
 */
class EvaluationIT extends IntegrationTest {

    @Test
    void theRubricsComeLoadedAsMacondoLabDeliveredThem() {
        String token = loginAs(Role.JUDGE);

        Response poster = get("/api/v1/rubrics/INNPRENDE_I", token).expect(200);
        List<String> posterCriteria = poster.json("$.criteria[*].shortName");
        assertThat(posterCriteria)
                .hasSize(6)
                .startsWith("Aspectos formales (nombre, autores, referencias)", "Planteamiento del problema");
        // En el póster los valores cambian según el criterio: así está en la tabla del documento.
        assertThat(scores(poster, 0)).containsExactly(0.0, 1.5, 4.0, 4.5, 5.0);
        assertThat(scores(poster, 1)).containsExactly(0.0, 1.5, 3.5, 4.5, 5.0);
        assertThat(scores(poster, 3)).containsExactly(1.5, 3.0, 4.0, 4.5, 5.0);
        List<String> posterLabels = poster.json("$.criteria[0].levels[*].label");
        assertThat(posterLabels).containsExactly("Insuficiente", "Deficiente", "Aceptable", "Bueno", "Excelente");

        Response pitch = get("/api/v1/rubrics/INNPRENDE_II", token).expect(200);
        List<String> pitchCriteria = pitch.json("$.criteria[*].name");
        assertThat(pitchCriteria)
                .containsExactly("Propuesta de valor", "PitchDeck", "Modelo de negocio", "Prototipo", "Competidores");
        for (int criterion = 0; criterion < 5; criterion++) {
            assertThat(scores(pitch, criterion)).containsExactly(0.0, 1.5, 3.0, 4.0, 4.5, 5.0);
        }
        assertThat(pitch.<String>json("$.criteria[3].levels[4].description")).startsWith("Prototipo funcional");

        get("/api/v1/rubrics/INNPRENDE_I", null).expect(401);
    }

    @Test
    void aJurorEvaluatesCorrectsAndTheTeacherSeesTheGrade() throws Exception {
        String macondolab = loginAs(Role.MACONDOLAB);
        String leaderEmail = createAccount(Role.STUDENT);
        String leader = login(leaderEmail, PASSWORD);
        String teacherEmail = createAccount(Role.TEACHER);
        String teacher = login(teacherEmail, PASSWORD);
        int projectId = createProject(leader, teacherEmail);
        String martaEmail = createAccount(Role.JUDGE);
        String marta = login(martaEmail, PASSWORD);
        String pedroEmail = createAccount(Role.TEACHER);
        String pedro = login(pedroEmail, PASSWORD);
        String mine = "/api/v1/projects/" + projectId + "/evaluations/mine";
        String results = "/api/v1/projects/" + projectId + "/evaluations";
        Response rubric = get("/api/v1/rubrics/INNPRENDE_I", macondolab).expect(200);

        // Sin asignación no hay nada que calificar: el proyecto ni se ve.
        get(mine, marta).expect(404);
        post("/api/v1/projects/" + projectId + "/jurors", macondolab, Map.of("email", martaEmail))
                .expect(201);
        post("/api/v1/projects/" + projectId + "/jurors", macondolab, Map.of("email", pedroEmail))
                .expect(201);
        get(mine, marta).expect(204);

        // Le falta un criterio, y un nivel por debajo de 3.0 pide observación.
        List<Map<String, Object>> incomplete = levels(rubric, 4, 4, 4, 4, 4);
        Response missing = put(mine, marta, Map.of("scores", incomplete)).expect(400);
        assertThat(missing.<String>json("$.fields['scores." + criterionId(rubric, 5) + ".levelId']"))
                .contains("Elige un nivel");
        List<Map<String, Object>> failing = levels(rubric, 4, 1, 4, 4, 4, 4);
        Response uncommented = put(mine, marta, Map.of("scores", failing)).expect(400);
        assertThat(uncommented.<String>json("$.fields['scores." + criterionId(rubric, 1) + ".comment']"))
                .contains("obligatoria por debajo de 3.0");

        // Con la observación entra: (5.0 + 1.5 + 5.0 + 5.0 + 5.0 + 5.0) / 6 = 4.4.
        failing.get(1).put("comment", "El problema quedó muy genérico.");
        Response saved = put(mine, marta, Map.of("scores", failing)).expect(200);
        assertThat(saved.<Double>json("$.grade")).isEqualTo(4.4);
        assertThat(saved.<String>json("$.scale")).isEqualTo("GOOD");
        assertThat(saved.<String>json("$.scores[1].comment")).isEqualTo("El problema quedó muy genérico.");

        // Corrige: ahora todo en el nivel más alto.
        Response corrected = put(mine, marta, Map.of("scores", levels(rubric, 4, 4, 4, 4, 4, 4)))
                .expect(200);
        assertThat(corrected.<Double>json("$.grade")).isEqualTo(5.0);
        assertThat(corrected.<Integer>json("$.id")).isEqualTo(saved.<Integer>json("$.id"));
        assertThat(get(mine, marta).expect(200).<List<Object>>json("$.scores")).hasSize(6);
        List<Integer> martaDone =
                get("/api/v1/evaluations/mine", marta).expect(200).json("$[*].projectId");
        assertThat(martaDone).containsExactly(projectId);

        // Con uno de dos jurados, la nota ya existe y se ve quién falta.
        Response half = get(results, teacher).expect(200);
        assertThat(half.<Double>json("$.grade")).isEqualTo(5.0);
        assertThat(half.<Integer>json("$.jurors")).isEqualTo(2);
        List<Integer> pending = half.json("$.pending[*].userId");
        assertThat(pending).containsExactly(idOf(pedroEmail));

        // El listado y el CSV de la gestión llevan la nota y cuántos jurados van.
        String directory = "/api/v1/projects?size=200&editionId=" + openEdition() + "&track=INNPRENDE_I";
        List<Double> listed =
                get(directory, macondolab).expect(200).json("$.items[?(@.id == " + projectId + ")].grade");
        assertThat(listed).containsExactly(5.0);
        List<Integer> evaluated =
                get(directory, macondolab).expect(200).json("$.items[?(@.id == " + projectId + ")].evaluated");
        assertThat(evaluated).containsExactly(1);
        String csv = new String(
                get("/api/v1/projects/export?editionId=" + openEdition(), macondolab)
                        .expect(200)
                        .bytes(),
                StandardCharsets.UTF_8);
        assertThat(csv).contains("Nota;Jurados que calificaron").contains(";5.0;1 de 2;");

        // La gestión recuerda: solo a Pedro, que es quien tiene este proyecto pendiente.
        Response reminded = post(
                        "/api/v1/evaluations/reminders?editionId=" + openEdition() + "&track=INNPRENDE_I",
                        macondolab,
                        null)
                .expect(200);
        assertThat(reminded.<Integer>json("$.jurors")).isGreaterThanOrEqualTo(1);
        String mail = body(awaitMail(pedroEmail, "Tienes proyectos por calificar"));
        assertThat(mail)
                .contains("Prueba TEACHER")
                .contains("- Proyecto por evaluar")
                .contains("/jurado/proyectos");
        assertThat(MAIL.getReceivedMessages()).noneMatch(message -> {
            try {
                return message.getSubject().startsWith("Tienes proyectos")
                        && message.getAllRecipients()[0].toString().equalsIgnoreCase(martaEmail);
            } catch (Exception e) {
                return false;
            }
        });
        post("/api/v1/evaluations/reminders?editionId=" + openEdition() + "&track=INNPRENDE_I", pedro, null)
                .expect(403);

        // El segundo jurado marca que el equipo no asistió: 0.0, y el promedio baja a 2.5.
        Response absent = put(mine, pedro, Map.of("absent", true)).expect(200);
        assertThat(absent.<Double>json("$.grade")).isEqualTo(0.0);
        assertThat(absent.<List<Object>>json("$.scores")).isEmpty();
        Response both = get(results, macondolab).expect(200);
        assertThat(both.<Double>json("$.grade")).isEqualTo(2.5);
        assertThat(both.<String>json("$.scale")).isEqualTo("FAILING");
        assertThat(both.<List<Object>>json("$.pending")).isEmpty();

        // Las notas no son para el equipo ni para los jurados; y el profesor del grupo no califica.
        get(results, leader).expect(403);
        get(results, marta).expect(403);
        put(mine, teacher, Map.of("absent", true)).expect(403);
        get(results, loginAs(Role.JUDGE)).expect(404);

        // El equipo no ve nada hasta que la gestión publica; después, la nota y las observaciones sin nombres.
        String grade = "/api/v1/projects/" + projectId + "/grade";
        String publication = "/api/v1/editions/" + openEdition() + "/tracks/INNPRENDE_I/grades-publication";
        get(grade, leader).expect(204);
        put(publication, teacher, null).expect(403);
        Response published = put(publication, macondolab, null).expect(200);
        List<Object> publishedAt = published.json("$.tracks[?(@.track == 'INNPRENDE_I')].gradesPublishedAt");
        assertThat(publishedAt).singleElement().isNotNull();
        Response visible = get(grade, leader).expect(200);
        assertThat(visible.<Double>json("$.grade")).isEqualTo(2.5);
        assertThat(visible.<String>json("$.scale")).isEqualTo("FAILING");
        assertThat(visible.<Integer>json("$.evaluated")).isEqualTo(2);
        assertThat(visible.body()).doesNotContain("Prueba JUDGE").doesNotContain("juror");
        // Marta corrigió a todo 5.0 sin observaciones y Pedro no asistió: no hay observaciones que mostrar.
        assertThat(visible.<List<Object>>json("$.criteria")).isEmpty();
        get(grade, marta).expect(403);
        delete(publication, macondolab).expect(200);
        get(grade, leader).expect(204);

        // Si la gestión quita a un jurado, su evaluación deja de contar.
        delete("/api/v1/projects/" + projectId + "/jurors/" + idOf(pedroEmail), macondolab)
                .expect(204);
        Response afterRemoval = get(results, teacher).expect(200);
        assertThat(afterRemoval.<Double>json("$.grade")).isEqualTo(5.0);
        assertThat(afterRemoval.<Integer>json("$.jurors")).isEqualTo(1);

        // Una cuenta que ya calificó no se elimina.
        Response kept = delete("/api/v1/admin/users/" + idOf(martaEmail), loginAs(Role.ADMIN))
                .expect(409);
        assertThat(kept.<String>json("$.detail")).contains("ya calificó");
    }

    // ── Datos de apoyo ──────────────────────────────────────────────────────

    @Test
    void correctingARubricValueDoesNotRewriteTheGradesAlreadyGiven() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String teacherEmail = createAccount(Role.TEACHER);
        int projectId = createProject(loginAs(Role.STUDENT), teacherEmail);
        String martaEmail = createAccount(Role.JUDGE);
        String marta = login(martaEmail, PASSWORD);
        String mine = "/api/v1/projects/" + projectId + "/evaluations/mine";
        String results = "/api/v1/projects/" + projectId + "/evaluations";
        Response rubric = get("/api/v1/rubrics/INNPRENDE_I", macondolab).expect(200);
        post("/api/v1/projects/" + projectId + "/jurors", macondolab, Map.of("email", martaEmail))
                .expect(201);

        // Todo en el nivel más alto: 5.0.
        List<Map<String, Object>> excellent = levels(rubric, 4, 4, 4, 4, 4, 4);
        int evaluationId =
                put(mine, marta, Map.of("scores", excellent)).expect(200).json("$.id");
        int levelId = (int) excellent.getFirst().get("levelId");

        // La rúbrica es de todas las pruebas: se deja como estaba pase lo que pase.
        jdbc.update("UPDATE rubric_levels SET score = 4.0 WHERE id = ?", levelId);
        try {
            // La rúbrica ya muestra el valor corregido...
            assertThat(scores(get("/api/v1/rubrics/INNPRENDE_I", marta).expect(200), 0))
                    .endsWith(4.0);

            // ...pero lo que Marta calificó sigue valiendo lo que valía, para ella y para quien ve las notas.
            Response kept = get(mine, marta).expect(200);
            assertThat(kept.<Double>json("$.grade")).isEqualTo(5.0);
            assertThat(kept.<Double>json("$.scores[0].score")).isEqualTo(5.0);
            assertThat(get(results, macondolab).expect(200).<Double>json("$.grade"))
                    .isEqualTo(5.0);
            assertThat(get(results, login(teacherEmail, PASSWORD)).expect(200).<Double>json("$.grade"))
                    .isEqualTo(5.0);

            // Si vuelve a guardar, califica con la rúbrica de ahora: (4.0 + 5.0 × 5) / 6 = 4.83, que queda en 4.8.
            Response again = put(mine, marta, Map.of("scores", excellent)).expect(200);
            assertThat(again.<Double>json("$.grade")).isEqualTo(4.8);
            assertThat(again.<Double>json("$.scores[0].score")).isEqualTo(4.0);
            assertThat(again.<Integer>json("$.id")).isEqualTo(evaluationId);
        } finally {
            jdbc.update("UPDATE rubric_levels SET score = 5.0 WHERE id = ?", levelId);
        }

        // Devolver el valor a la rúbrica tampoco mueve lo guardado.
        assertThat(get(mine, marta).expect(200).<Double>json("$.grade")).isEqualTo(4.8);
    }

    @Test
    void aNewVersionOfTheRubricDoesNotReachAnEditionThatAlreadyHasItsOwn() {
        String macondolab = loginAs(Role.MACONDOLAB);
        // Inscribir el proyecto crea la edición abierta, con las rúbricas de hoy.
        int projectId = createProject(loginAs(Role.STUDENT), createAccount(Role.TEACHER));
        String martaEmail = createAccount(Role.JUDGE);
        String marta = login(martaEmail, PASSWORD);
        post("/api/v1/projects/" + projectId + "/jurors", macondolab, Map.of("email", martaEmail))
                .expect(201);
        String mine = "/api/v1/projects/" + projectId + "/evaluations/mine";
        String ofProject = "/api/v1/projects/" + projectId + "/rubric";
        String rubricOfEdition = "$.tracks[?(@.track == 'INNPRENDE_I')].rubricId";

        Response first = get(ofProject, marta).expect(200);
        int firstId = first.json("$.id");
        assertThat(first.<Integer>json("$.version")).isEqualTo(1);
        assertThat(get("/api/v1/editions/" + openEdition(), null).expect(200).<List<Integer>>json(rubricOfEdition))
                .containsExactly(firstId);
        // Quien no ve el proyecto tampoco ve con qué se califica.
        get(ofProject, loginAs(Role.STUDENT)).expect(404);

        // MacondoLab entrega otra rúbrica del póster: entra como versión 2, con un solo criterio.
        jdbc.update("INSERT INTO rubrics (track, version, name) VALUES ('INNPRENDE_I', 2, 'Póster, segunda versión')");
        int secondId = jdbc.queryForObject(
                "SELECT id FROM rubrics WHERE track = 'INNPRENDE_I' AND version = 2", Integer.class);
        jdbc.update(
                "INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES (?, 1, 'Criterio único', 'Único')",
                secondId);
        int criterionId =
                jdbc.queryForObject("SELECT id FROM rubric_criteria WHERE rubric_id = ?", Integer.class, secondId);
        jdbc.update(
                "INSERT INTO rubric_levels (criterion_id, position, label, score, description)"
                        + " VALUES (?, 1, 'Excelente', 5.0, 'Cumple todo')",
                criterionId);
        int levelId =
                jdbc.queryForObject("SELECT id FROM rubric_levels WHERE criterion_id = ?", Integer.class, criterionId);
        Integer laterEdition = null;
        // Las rúbricas son de todas las pruebas: la versión 2 se quita pase lo que pase.
        try {
            // La vigente ya es la nueva...
            assertThat(get("/api/v1/rubrics/INNPRENDE_I", marta).expect(200).<Integer>json("$.id"))
                    .isEqualTo(secondId);

            // ...pero el proyecto se sigue calificando con la de su edición.
            Response still = get(ofProject, marta).expect(200);
            assertThat(still.<Integer>json("$.id")).isEqualTo(firstId);
            assertThat(still.<List<Object>>json("$.criteria")).hasSize(6);
            Response saved = put(mine, marta, Map.of("scores", levels(still, 4, 4, 4, 4, 4, 4)))
                    .expect(200);
            assertThat(saved.<Double>json("$.grade")).isEqualTo(5.0);
            Response rejected = put(
                            mine,
                            marta,
                            Map.of("scores", List.of(Map.of("criterionId", criterionId, "levelId", levelId))))
                    .expect(400);
            assertThat(rejected.<String>json("$.fields.scores")).contains("rúbrica de esta edición");

            // Una edición creada desde ahora nace con la versión nueva, y la otra cátedra con la suya.
            Map<String, Object> later = new HashMap<>();
            later.put("name", "Expoideas con otra rúbrica " + System.nanoTime());
            later.put("registrationOpensOn", "2045-03-01");
            later.put("registrationClosesOn", "2045-03-15");
            later.put("submissionClosesOn", "2045-03-30");
            later.put(
                    "tracks",
                    List.of(
                            Map.of("track", "INNPRENDE_I", "minMembers", 1, "maxMembers", 3),
                            Map.of("track", "INNPRENDE_II", "minMembers", 1, "maxMembers", 3)));
            Response created = post("/api/v1/editions", macondolab, later).expect(201);
            laterEdition = created.json("$.id");
            assertThat(created.<List<Integer>>json(rubricOfEdition)).containsExactly(secondId);

            // Cambiarle los cupos no la mueve de rúbrica, ni a la abierta.
            later.put(
                    "tracks",
                    List.of(
                            Map.of("track", "INNPRENDE_I", "minMembers", 2, "maxMembers", 5),
                            Map.of("track", "INNPRENDE_II", "minMembers", 1, "maxMembers", 3)));
            assertThat(put("/api/v1/editions/" + laterEdition, macondolab, later)
                            .expect(200)
                            .<List<Integer>>json(rubricOfEdition))
                    .containsExactly(secondId);
            assertThat(get("/api/v1/editions/" + openEdition(), null)
                            .expect(200)
                            .<List<Integer>>json(rubricOfEdition))
                    .containsExactly(firstId);
        } finally {
            if (laterEdition != null) {
                jdbc.update("DELETE FROM edition_tracks WHERE edition_id = ?", laterEdition);
                jdbc.update("DELETE FROM editions WHERE id = ?", laterEdition);
            }
            jdbc.update("DELETE FROM rubrics WHERE id = ?", secondId);
        }

        // Sin la versión 2, la vigente vuelve a ser la de la edición.
        assertThat(get("/api/v1/rubrics/INNPRENDE_I", marta).expect(200).<Integer>json("$.id"))
                .isEqualTo(firstId);
    }

    private int createProject(String leaderToken, String teacherEmail) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", "INNPRENDE_I");
        body.put("title", "Proyecto por evaluar " + System.nanoTime());
        body.put("summary", "Una propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(teacherEmail));
        body.put("semester", 5);
        body.put("phone", "3001234567");
        return post("/api/v1/projects", leaderToken, body).expect(201).json("$.id");
    }

    private static List<Double> scores(Response rubric, int criterion) {
        return rubric.json("$.criteria[" + criterion + "].levels[*].score");
    }

    private static int criterionId(Response rubric, int criterion) {
        return rubric.json("$.criteria[" + criterion + "].id");
    }

    /** Un nivel por criterio, en orden: la posición (desde 0) del nivel elegido en cada uno. */
    private static List<Map<String, Object>> levels(Response rubric, int... positions) {
        List<Map<String, Object>> scores = new ArrayList<>();
        for (int criterion = 0; criterion < positions.length; criterion++) {
            Map<String, Object> score = new HashMap<>();
            score.put("criterionId", criterionId(rubric, criterion));
            score.put(
                    "levelId",
                    rubric.<Integer>json("$.criteria[" + criterion + "].levels[" + positions[criterion] + "].id"));
            scores.add(score);
        }
        return scores;
    }

    @Test
    void anEmptySlotAmongTheScoresIsRejectedNotAnError() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int projectId = createProject(loginAs(Role.STUDENT), createAccount(Role.TEACHER));
        String martaEmail = createAccount(Role.JUDGE);
        post("/api/v1/projects/" + projectId + "/jurors", macondolab, Map.of("email", martaEmail))
                .expect(201);

        put(
                        "/api/v1/projects/" + projectId + "/evaluations/mine",
                        login(martaEmail, PASSWORD),
                        Map.of("scores", Arrays.asList((Object) null)))
                .expect(400);
    }
}
