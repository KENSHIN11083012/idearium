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
 * Tipos de prototipo contra MySQL: el catálogo lo escribe la gestión, solo los
 * proyectos de INNPRENDE II llevan tipo, y cada proyecto ve los entregables
 * generales más los de su tipo.
 */
class PrototypeIT extends IntegrationTest {

    @Test
    void managementWritesTheCatalogAndAnyoneReadsIt() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String name = "Prototipo digital " + System.nanoTime();

        post("/api/v1/prototype-types", loginAs(Role.STUDENT), Map.of("name", name))
                .expect(403);
        int id = post("/api/v1/prototype-types", macondolab, Map.of("name", name))
                .expect(201)
                .json("$.id");
        post("/api/v1/prototype-types", macondolab, Map.of("name", name)).expect(409);
        put("/api/v1/prototype-types/" + id, macondolab, Map.of("name", name + " (app)"))
                .expect(200);

        List<String> names = get("/api/v1/prototype-types", null).expect(200).json("$[*].name");
        assertThat(names).contains(name + " (app)");
    }

    @Test
    void onlyTrackTwoProjectsCarryAPrototypeTypeAndTheirDeliverablesFollowIt() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int digital = createPrototypeType(macondolab, "Digital");
        int physical = createPrototypeType(macondolab, "Físico");
        approveManually(macondolab);

        // En I no se acepta; en II, con catálogo cargado, es obligatorio.
        Map<String, Object> trackOne = projectBody("INNPRENDE_I");
        trackOne.put("prototypeTypeId", digital);
        Response refused = post("/api/v1/projects", leaderToken, trackOne).expect(400);
        assertThat(refused.json("$.fields.prototypeTypeId").toString()).contains("solo para INNPRENDE II");
        Map<String, Object> trackTwo = projectBody("INNPRENDE_II");
        Response missing = post("/api/v1/projects", leaderToken, trackTwo).expect(400);
        assertThat(missing.json("$.fields.prototypeTypeId").toString()).contains("Elige el tipo");
        trackTwo.put("prototypeTypeId", 999_999);
        post("/api/v1/projects", leaderToken, trackTwo).expect(404);

        trackTwo.put("prototypeTypeId", digital);
        Response created = post("/api/v1/projects", leaderToken, trackTwo).expect(201);
        int projectId = created.json("$.id");
        assertThat(created.<String>json("$.prototypeType")).startsWith("Digital");

        // Entregables: uno general, uno para digital y uno para físico. Un tipo por prototipo no cabe en I.
        int general = createType(macondolab, "INNPRENDE_II", "Pitch", null);
        int forDigital = createType(macondolab, "INNPRENDE_II", "Video del prototipo", digital);
        int forPhysical = createType(macondolab, "INNPRENDE_II", "Fotos del prototipo", physical);
        Map<String, Object> wrongTrack = typeBody("INNPRENDE_I", "Póster", physical);
        Response wrong =
                post("/api/v1/deliverable-types", macondolab, wrongTrack).expect(400);
        assertThat(wrong.<String>json("$.fields.prototypeTypeId")).isNotBlank();

        List<Integer> visible = get("/api/v1/projects/" + projectId + "/deliverables", leaderToken)
                .expect(200)
                .json("$[*].type.id");
        assertThat(visible).contains(general, forDigital).doesNotContain(forPhysical);
        List<Integer> required = get(
                        "/api/v1/projects?size=200&editionId=" + openEdition() + "&track=INNPRENDE_II", macondolab)
                .expect(200)
                .json("$.items[?(@.id == " + projectId + ")].requiredDeliverables");
        assertThat(required).containsExactly(2);
        List<String> summaryType = get(
                        "/api/v1/projects?size=200&editionId=" + openEdition() + "&track=INNPRENDE_II", macondolab)
                .expect(200)
                .json("$.items[?(@.id == " + projectId + ")].prototypeType");
        assertThat(summaryType).singleElement().asString().startsWith("Digital");

        // Subir a un entregable de otro tipo es como si no existiera.
        postFile(
                        "/api/v1/projects/" + projectId + "/deliverables?deliverableTypeId=" + forPhysical,
                        leaderToken,
                        "file",
                        "foto.jpg",
                        MediaType.IMAGE_JPEG,
                        TestData.JPEG)
                .expect(404);

        // Cambiar de tipo cambia lo que se pide.
        trackTwo.put("prototypeTypeId", physical);
        put("/api/v1/projects/" + projectId, leaderToken, trackTwo).expect(200);
        List<Integer> afterChange = get("/api/v1/projects/" + projectId + "/deliverables", leaderToken)
                .expect(200)
                .json("$[*].type.id");
        assertThat(afterChange).contains(general, forPhysical).doesNotContain(forDigital);
    }

    // ── Datos de apoyo ──────────────────────────────────────────────────────

    private String leaderToken;
    private String teacherEmail;

    private void approveManually(String macondolab) {
        String leader = createAccount(Role.STUDENT);
        leaderToken = login(leader, PASSWORD);
        teacherEmail = createAccount(Role.TEACHER);
        // Para inscribirse en II hay que haber aprobado I.
        post("/api/v1/admin/track-approvals", macondolab, Map.of("userId", idOf(leader), "track", "INNPRENDE_I"))
                .expect(201);
    }

    private Map<String, Object> projectBody(String track) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", track);
        body.put("title", "Prototipo " + System.nanoTime());
        body.put("summary", "Una propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(teacherEmail));
        body.put("semester", 5);
        body.put("phone", "3001234567");
        return body;
    }

    private int createPrototypeType(String token, String name) {
        return post("/api/v1/prototype-types", token, Map.of("name", name + " " + System.nanoTime()))
                .expect(201)
                .json("$.id");
    }

    private int createType(String token, String track, String name, Integer prototypeTypeId) {
        return post("/api/v1/deliverable-types", token, typeBody(track, name, prototypeTypeId))
                .expect(201)
                .json("$.id");
    }

    private Map<String, Object> typeBody(String track, String name, Integer prototypeTypeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", track);
        body.put("name", name + " " + System.nanoTime());
        body.put("kind", "ANY");
        body.put("required", true);
        body.put("maxFiles", 3);
        body.put("sortOrder", 1);
        body.put("prototypeTypeId", prototypeTypeId);
        return body;
    }
}
