package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Jurados por asignación contra MySQL: a quién se puede asignar y qué ve un
 * jurado una vez asignado (su proyecto y sus entregables, no los demás).
 */
class JuryIT extends IntegrationTest {

    private static final String TRACK = "INNPRENDE_I";

    @Test
    void managementAssignsJurorsButNotTheGroupTeacherNorTheTeam() {
        String macondolab = loginAs(Role.MACONDOLAB);
        Team team = newTeam();
        int projectId = createProject(team);
        String jurors = "/api/v1/projects/" + projectId + "/jurors";
        String judge = createAccount(Role.JUDGE);
        String otherTeacher = createAccount(Role.TEACHER);

        Response teacher =
                post(jurors, macondolab, Map.of("email", team.teacherEmail())).expect(400);
        assertThat(teacher.json("$.fields.email").toString()).contains("profesor del grupo");
        Response member =
                post(jurors, macondolab, Map.of("email", team.leaderEmail())).expect(400);
        assertThat(member.json("$.fields.email").toString()).contains("en el equipo");
        Response student = post(jurors, macondolab, Map.of("email", createAccount(Role.STUDENT)))
                .expect(400);
        assertThat(student.json("$.fields.email").toString()).contains("Solo profesores, jurados");
        Response unknown = post(jurors, macondolab, Map.of("email", "nadie@unisimon.edu.co"))
                .expect(400);
        assertThat(unknown.<String>json("$.fields.email")).isNotBlank();

        post(jurors, macondolab, Map.of("email", judge)).expect(201);
        post(jurors, macondolab, Map.of("email", otherTeacher)).expect(201);
        post(jurors, macondolab, Map.of("email", judge)).expect(409);

        List<String> emails = get(jurors, macondolab).expect(200).json("$[*].email");
        assertThat(emails).containsExactly(judge, otherTeacher);

        // Asignar es cosa de la gestión: ni el profesor del grupo ni el líder.
        post(jurors, login(team.teacherEmail(), PASSWORD), Map.of("email", judge))
                .expect(403);
        get(jurors, team.leaderToken()).expect(403);

        delete(jurors + "/" + idOf(otherTeacher), macondolab).expect(204);
        delete(jurors + "/" + idOf(otherTeacher), macondolab).expect(404);
        List<String> left = get(jurors, macondolab).expect(200).json("$[*].email");
        assertThat(left).containsExactly(judge);
    }

    @Test
    void anAssignedJurorSeesTheProjectAndItsFilesAndNothingElse() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab);
        Team team = newTeam();
        int mine = createProject(team);
        int other = createProject(newTeam());
        String judgeEmail = createAccount(Role.JUDGE);
        String judge = login(judgeEmail, PASSWORD);

        // Antes de la asignación, el jurado no tiene nada.
        List<Integer> none = get("/api/v1/jury/projects", judge).expect(200).json("$[*].id");
        assertThat(none).isEmpty();
        get("/api/v1/projects/" + mine, judge).expect(404);

        post("/api/v1/projects/" + mine + "/jurors", macondolab, Map.of("email", judgeEmail))
                .expect(201);
        Response uploaded = postFile(
                        "/api/v1/projects/" + mine + "/deliverables?deliverableTypeId=" + typeId,
                        team.leaderToken(),
                        "file",
                        "poster.pdf",
                        MediaType.APPLICATION_PDF,
                        TestData.PDF)
                .expect(200);
        String fileId = ((List<String>) uploaded.json("$[?(@.type.id == " + typeId + ")].files[*].fileId")).getFirst();

        List<Integer> assigned = get("/api/v1/jury/projects", judge).expect(200).json("$[*].id");
        assertThat(assigned).containsExactly(mine);
        get("/api/v1/projects/" + mine, judge).expect(200);
        get("/api/v1/projects/" + mine + "/deliverables", judge).expect(200);
        get("/api/v1/files/" + fileId, judge).expect(200);
        get("/api/v1/projects/" + mine + "/presentation", judge).expect(204);

        // El otro proyecto sigue siendo invisible, y mirar no es tocar.
        get("/api/v1/projects/" + other, judge).expect(404);
        get("/api/v1/projects/" + other + "/deliverables", judge).expect(404);
        postFile(
                        "/api/v1/projects/" + mine + "/deliverables?deliverableTypeId=" + typeId,
                        judge,
                        "file",
                        "otro.pdf",
                        MediaType.APPLICATION_PDF,
                        TestData.PDF)
                .expect(403);

        // Al quitarlo, vuelve a no ver nada.
        delete("/api/v1/projects/" + mine + "/jurors/" + idOf(judgeEmail), macondolab)
                .expect(204);
        get("/api/v1/projects/" + mine, judge).expect(404);
        get("/api/v1/files/" + fileId, judge).expect(404);
    }

    // ── Datos de apoyo ──────────────────────────────────────────────────────

    private record Team(String leaderEmail, String leaderToken, String teacherEmail) {}

    private Team newTeam() {
        String leader = createAccount(Role.STUDENT);
        return new Team(leader, login(leader, PASSWORD), createAccount(Role.TEACHER));
    }

    private int createProject(Team team) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", TRACK);
        body.put("title", "Proyecto con jurados " + System.nanoTime());
        body.put("summary", "Una propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(team.teacherEmail()));
        return post("/api/v1/projects", team.leaderToken(), body).expect(201).json("$.id");
    }

    private int createType(String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", TRACK);
        body.put("name", "Póster para jurados " + System.nanoTime());
        body.put("kind", "DOCUMENT");
        body.put("required", true);
        body.put("maxFiles", 1);
        body.put("sortOrder", 1);
        return post("/api/v1/deliverable-types", token, body).expect(201).json("$.id");
    }
}
