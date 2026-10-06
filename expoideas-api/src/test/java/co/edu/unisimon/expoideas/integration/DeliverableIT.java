package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Entregables contra MySQL: MacondoLab configura qué pide cada cátedra y el
 * equipo sube, descarga y quita sus archivos hasta el cierre de entregas.
 */
class DeliverableIT extends IntegrationTest {

    private static final String TRACK = "INNPRENDE_I";

    // ── Configuración de los entregables ────────────────────────────────────

    @Test
    void macondoLabConfiguresWhatEachTrackAsks() {
        String macondolab = loginAs(Role.MACONDOLAB);

        int posterId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);

        List<Integer> ids = get("/api/v1/deliverable-types?editionId=" + openEdition() + "&track=" + TRACK, macondolab)
                .expect(200)
                .json("$[*].id");
        assertThat(ids).contains(posterId);

        Map<String, Object> changed = typeBody("Póster final " + System.nanoTime(), "ANY", false, 3);
        Response updated = put("/api/v1/deliverable-types/" + posterId, macondolab, changed)
                .expect(200);
        assertThat(updated.json("$.maxFiles").toString()).isEqualTo("3");
        assertThat((Boolean) updated.json("$.required")).isFalse();

        delete("/api/v1/deliverable-types/" + posterId, macondolab).expect(204);
    }

    @Test
    void onlyManagementConfiguresDeliverables() {
        Map<String, Object> body = typeBody("Póster " + System.nanoTime(), "DOCUMENT", true, 1);

        post("/api/v1/deliverable-types", loginAs(Role.STUDENT), body).expect(403);
        post("/api/v1/deliverable-types", loginAs(Role.TEACHER), body).expect(403);
        post("/api/v1/deliverable-types", null, body).expect(401);
        // Leerlos sí pide sesión, pero de cualquier rol.
        get("/api/v1/deliverable-types?editionId=" + openEdition() + "&track=" + TRACK, null)
                .expect(401);
    }

    // ── Subida y descarga ───────────────────────────────────────────────────

    @Test
    void theTeamUploadsDownloadsAndRemovesItsFiles() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);
        Team team = newTeam();
        int projectId = createProject(team);

        Response pending = get("/api/v1/projects/" + projectId + "/deliverables", team.leaderToken())
                .expect(200);
        List<Boolean> complete = pending.json("$[?(@.type.id == " + typeId + ")].complete");
        assertThat(complete).containsExactly(false);

        Response uploaded = uploadPdf(projectId, typeId, team.leaderToken()).expect(200);
        List<Boolean> done = uploaded.json("$[?(@.type.id == " + typeId + ")].complete");
        assertThat(done).containsExactly(true);
        List<String> names = uploaded.json("$[?(@.type.id == " + typeId + ")].files[*].fileName");
        assertThat(names).containsExactly("poster.pdf");

        String fileId = ((List<String>) uploaded.json("$[?(@.type.id == " + typeId + ")].files[*].fileId")).getFirst();
        // El archivo es privado: lo abren el equipo y el docente, no cualquiera.
        get("/api/v1/files/" + fileId, team.leaderToken()).expect(200);
        get("/api/v1/files/" + fileId, login(team.teacherEmail(), PASSWORD)).expect(200);
        get("/api/v1/files/" + fileId, loginAs(Role.MACONDOLAB)).expect(200);
        get("/api/v1/files/" + fileId, loginAs(Role.STUDENT)).expect(404);
        get("/api/v1/files/" + fileId, null).expect(404);

        int deliverableId = ((List<Integer>) uploaded.json("$[?(@.type.id == " + typeId + ")].files[*].id")).getFirst();
        delete("/api/v1/projects/" + projectId + "/deliverables/" + deliverableId, team.leaderToken())
                .expect(200);
        get("/api/v1/files/" + fileId, team.leaderToken()).expect(404);
    }

    @Test
    void aDeliverableThatTakesOneFileDoesNotTakeTwo() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);
        Team team = newTeam();
        int projectId = createProject(team);

        uploadPdf(projectId, typeId, team.leaderToken()).expect(200);
        Response second = uploadPdf(projectId, typeId, team.leaderToken()).expect(409);
        assertThat(second.json("$.detail").toString()).contains("un solo archivo");
    }

    @Test
    void aDoubleClickOnASingleFileDeliverableStoresOneFile() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);

        // Una carrera no sale siempre: se repite para que, sin la protección, alguna vuelta falle.
        for (int round = 0; round < 5; round++) {
            Team team = newTeam();
            int projectId = createProject(team);

            List<Response> answers = atTheSameTime(
                    () -> uploadPdf(projectId, typeId, team.leaderToken()),
                    () -> uploadPdf(projectId, typeId, team.leaderToken()));

            assertThat(answers.stream().map(answer -> answer.status().value())).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM deliverables WHERE project_id = ? AND deliverable_type_id = ?",
                            Integer.class,
                            projectId,
                            typeId))
                    .isEqualTo(1);
        }
    }

    @Test
    void theFormatIsCheckedAgainstTheContentNotTheName() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Fotos " + System.nanoTime(), "IMAGE", true, 3);
        Team team = newTeam();
        int projectId = createProject(team);

        Response rejected = putOrPostFile(
                        projectId, typeId, team.leaderToken(), "foto.jpg", MediaType.IMAGE_JPEG, TestData.PDF)
                .expect(400);
        assertThat(rejected.json("$.fields.file").toString()).contains("Formato no permitido");

        putOrPostFile(projectId, typeId, team.leaderToken(), "foto.jpg", MediaType.IMAGE_JPEG, TestData.JPEG)
                .expect(200);
    }

    @Test
    void onlyTheTeamUploads() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);
        Team team = newTeam();
        int projectId = createProject(team);

        // El docente ve el proyecto, pero no sube por el equipo.
        uploadPdf(projectId, typeId, login(team.teacherEmail(), PASSWORD)).expect(403);
        // Alguien de fuera ni siquiera sabe que existe.
        uploadPdf(projectId, typeId, loginAs(Role.STUDENT)).expect(404);
        // Una invitación sin responder tampoco basta.
        post("/api/v1/projects/" + projectId + "/invitations", team.leaderToken(), Map.of("email", team.memberEmail()))
                .expect(201);
        uploadPdf(projectId, typeId, team.memberToken()).expect(403);
    }

    @Test
    void aDeliverableWithFilesCannotBeDeleted() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Evidencia " + System.nanoTime(), "ANY", true, 2);
        Team team = newTeam();
        int projectId = createProject(team);
        uploadPdf(projectId, typeId, team.leaderToken()).expect(200);

        Response refused =
                delete("/api/v1/deliverable-types/" + typeId, macondolab).expect(409);
        assertThat(refused.json("$.detail").toString()).contains("ya hay proyectos que lo entregaron");
    }

    @Test
    void deletingTheProjectTakesItsFilesAlong() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);
        Team team = newTeam();
        int projectId = createProject(team);
        Response uploaded = uploadPdf(projectId, typeId, team.leaderToken()).expect(200);
        String fileId = ((List<String>) uploaded.json("$[?(@.type.id == " + typeId + ")].files[*].fileId")).getFirst();

        delete("/api/v1/projects/" + projectId, team.leaderToken()).expect(204);

        get("/api/v1/files/" + fileId, team.leaderToken()).expect(404);
        get("/api/v1/projects/" + projectId + "/deliverables", team.leaderToken())
                .expect(404);
    }

    // ── Plantillas ──────────────────────────────────────────────────────────

    @Test
    void managementUploadsATemplateThatAnySessionDownloads() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);
        Team team = newTeam();
        int projectId = createProject(team);

        // Una imagen no es una plantilla; un PPTX sí (por su contenido, no por el nombre).
        Response rejected = putFile(
                        "/api/v1/deliverable-types/" + typeId + "/template",
                        macondolab,
                        "file",
                        "poster.pptx",
                        MediaType.APPLICATION_OCTET_STREAM,
                        TestData.JPEG)
                .expect(400);
        assertThat(rejected.json("$.fields.file").toString()).contains("PDF, DOCX o PPTX");

        Response withTemplate = putFile(
                        "/api/v1/deliverable-types/" + typeId + "/template",
                        macondolab,
                        "file",
                        "formato-poster.pptx",
                        MediaType.APPLICATION_OCTET_STREAM,
                        TestData.PPTX)
                .expect(200);
        String templateId = withTemplate.json("$.templateFileId");
        assertThat(withTemplate.<String>json("$.templateFileName")).isEqualTo("formato-poster.pptx");

        // El equipo la ve junto a su entregable y la descarga; también alguien de otro equipo. Sin sesión, no.
        Response groups = get("/api/v1/projects/" + projectId + "/deliverables", team.leaderToken())
                .expect(200);
        List<String> templates = groups.json("$[?(@.type.id == " + typeId + ")].type.templateFileId");
        assertThat(templates).containsExactly(templateId);
        get("/api/v1/files/" + templateId, team.leaderToken()).expect(200);
        get("/api/v1/files/" + templateId, loginAs(Role.STUDENT)).expect(200);
        get("/api/v1/files/" + templateId, login(team.teacherEmail(), PASSWORD)).expect(200);
        get("/api/v1/files/" + templateId, null).expect(404);

        // Reemplazarla borra la anterior; quitarla, la actual.
        String replaced = putFile(
                        "/api/v1/deliverable-types/" + typeId + "/template",
                        macondolab,
                        "file",
                        "formato.docx",
                        MediaType.APPLICATION_OCTET_STREAM,
                        TestData.DOCX)
                .expect(200)
                .json("$.templateFileId");
        get("/api/v1/files/" + templateId, team.leaderToken()).expect(404);
        get("/api/v1/files/" + replaced, team.leaderToken()).expect(200);

        Response without = delete("/api/v1/deliverable-types/" + typeId + "/template", macondolab)
                .expect(200);
        assertThat(without.<String>json("$.templateFileId")).isNull();
        get("/api/v1/files/" + replaced, team.leaderToken()).expect(404);
    }

    @Test
    void anAccountWithTemplatesIsNotDeletedAndDeletingTheTypeTakesTheTemplate() {
        String admin = loginAs(Role.ADMIN);
        String uploaderEmail = createAccount(Role.MACONDOLAB);
        String uploader = login(uploaderEmail, PASSWORD);
        int typeId = createType(uploader, "Carta " + System.nanoTime(), "DOCUMENT", false, 1);
        String templateId = putFile(
                        "/api/v1/deliverable-types/" + typeId + "/template",
                        uploader,
                        "file",
                        "carta.pdf",
                        MediaType.APPLICATION_PDF,
                        TestData.PDF)
                .expect(200)
                .json("$.templateFileId");

        Response kept =
                delete("/api/v1/admin/users/" + idOf(uploaderEmail), admin).expect(409);
        assertThat(kept.json("$.detail").toString()).contains("plantillas");

        delete("/api/v1/deliverable-types/" + typeId, uploader).expect(204);
        get("/api/v1/files/" + templateId, uploader).expect(404);
        delete("/api/v1/admin/users/" + idOf(uploaderEmail), admin).expect(204);
    }

    @Test
    @SuppressWarnings("unchecked")
    void whoIsOnlyInvitedDoesNotSeeTheDeliverablesUntilAccepting() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int typeId = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);
        Team team = newTeam();
        int projectId = createProject(team);
        Response uploaded = uploadPdf(projectId, typeId, team.leaderToken()).expect(200);
        String fileId = ((List<String>) uploaded.json("$[?(@.type.id == " + typeId + ")].files[*].fileId")).getFirst();
        post("/api/v1/projects/" + projectId + "/invitations", team.leaderToken(), Map.of("email", team.memberEmail()))
                .expect(201);
        String deliverables = "/api/v1/projects/" + projectId + "/deliverables";

        // La ficha la ve, para decidir si acepta; el trabajo del equipo todavía no es suyo.
        get("/api/v1/projects/" + projectId, team.memberToken()).expect(200);
        Response refused = get(deliverables, team.memberToken()).expect(403);
        assertThat(refused.<String>json("$.detail")).contains("Acepta la invitación");
        get("/api/v1/files/" + fileId, team.memberToken()).expect(404);

        int invitationId = ((List<Integer>) get("/api/v1/invitations", team.memberToken())
                        .expect(200)
                        .json("$[?(@.projectId == " + projectId + ")].id"))
                .getFirst();
        post("/api/v1/invitations/" + invitationId + "/acceptance", team.memberToken(), null)
                .expect(200);

        get(deliverables, team.memberToken()).expect(200);
        get("/api/v1/files/" + fileId, team.memberToken()).expect(200);
    }

    // ── Enlaces ─────────────────────────────────────────────────────────────

    @Test
    void aLinkDeliverableTakesAnAddressNotAFile() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int linkType = createType(macondolab, "Video " + System.nanoTime(), "LINK", true, 1);
        int fileType = createType(macondolab, "Póster " + System.nanoTime(), "DOCUMENT", true, 1);
        Team team = newTeam();
        int projectId = createProject(team);
        String links = "/api/v1/projects/" + projectId + "/deliverables/links";

        Response notAnAddress = post(
                        links, team.leaderToken(), Map.of("deliverableTypeId", linkType, "url", "youtu.be/abc"))
                .expect(400);
        assertThat(notAnAddress.<String>json("$.fields.url")).isNotBlank();
        Response noHost = post(links, team.leaderToken(), Map.of("deliverableTypeId", linkType, "url", "https://"))
                .expect(400);
        assertThat(noHost.<String>json("$.fields.url")).isNotBlank();

        Response wrongKind = post(
                        links, team.leaderToken(), Map.of("deliverableTypeId", fileType, "url", "https://youtu.be/abc"))
                .expect(400);
        assertThat(wrongKind.json("$.fields.url").toString()).contains("como archivo");
        Response wrongKindFile =
                uploadPdf(projectId, linkType, team.leaderToken()).expect(400);
        assertThat(wrongKindFile.json("$.fields.file").toString()).contains("como enlace");

        Response registered = post(
                        links,
                        team.leaderToken(),
                        Map.of("deliverableTypeId", linkType, "url", " https://youtu.be/abc "))
                .expect(200);
        List<String> urls = registered.json("$[?(@.type.id == " + linkType + ")].files[*].url");
        assertThat(urls).containsExactly("https://youtu.be/abc");
        List<Boolean> complete = registered.json("$[?(@.type.id == " + linkType + ")].complete");
        assertThat(complete).containsExactly(true);

        Response second = post(
                        links, team.leaderToken(), Map.of("deliverableTypeId", linkType, "url", "https://otro.link"))
                .expect(409);
        assertThat(second.json("$.detail").toString()).contains("un solo enlace");

        // El profesor no registra por el equipo; el equipo puede quitarlo.
        post(links, login(team.teacherEmail(), PASSWORD), Map.of("deliverableTypeId", linkType, "url", "https://x.y"))
                .expect(403);
        int deliverableId =
                ((List<Integer>) registered.json("$[?(@.type.id == " + linkType + ")].files[*].id")).getFirst();
        Response removed = delete(
                        "/api/v1/projects/" + projectId + "/deliverables/" + deliverableId, team.leaderToken())
                .expect(200);
        List<Boolean> pending = removed.json("$[?(@.type.id == " + linkType + ")].complete");
        assertThat(pending).containsExactly(false);
    }

    // ── Cierre propio del entregable ────────────────────────────────────────

    @Test
    void aDeliverableWithItsOwnDeadlineFollowsItInsteadOfTheEditionOne() {
        String macondolab = loginAs(Role.MACONDOLAB);
        // La API compara con la fecha de Bogotá (TimeConfig): con la del equipo, de noche en UTC "ayer" aún es hoy
        // allá.
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
        Map<String, Object> closed = typeBody("Fotos cerradas " + System.nanoTime(), "IMAGE", false, 10);
        closed.put("closesOn", today.minusDays(1).toString());
        int closedType = post("/api/v1/deliverable-types", macondolab, closed)
                .expect(201)
                .json("$.id");
        Map<String, Object> open = typeBody("Fotos abiertas " + System.nanoTime(), "IMAGE", false, 10);
        open.put("closesOn", today.toString());
        Response created = post("/api/v1/deliverable-types", macondolab, open).expect(201);
        assertThat(created.<String>json("$.closesOn")).isEqualTo(today.toString());
        int openType = created.json("$.id");
        Team team = newTeam();
        int projectId = createProject(team);

        // La edición sigue abierta, pero ese entregable ya cerró.
        Response late = putOrPostFile(
                        projectId, closedType, team.leaderToken(), "foto.jpg", MediaType.IMAGE_JPEG, TestData.JPEG)
                .expect(409);
        assertThat(late.json("$.detail").toString()).contains(today.minusDays(1).toString());
        putOrPostFile(projectId, openType, team.leaderToken(), "foto.jpg", MediaType.IMAGE_JPEG, TestData.JPEG)
                .expect(200);
    }

    // ── Datos de apoyo ──────────────────────────────────────────────────────

    private record Team(
            String leaderEmail, String leaderToken, String memberEmail, String memberToken, String teacherEmail) {}

    private Team newTeam() {
        String leader = createAccount(Role.STUDENT);
        String member = createAccount(Role.STUDENT);
        String teacher = createAccount(Role.TEACHER);
        return new Team(leader, login(leader, PASSWORD), member, login(member, PASSWORD), teacher);
    }

    private int createProject(Team team) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", TRACK);
        body.put("title", "Proyecto con entregables");
        body.put("summary", "Una propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(team.teacherEmail()));
        return post("/api/v1/projects", team.leaderToken(), body).expect(201).json("$.id");
    }

    private int createType(String token, String name, String kind, boolean required, int maxFiles) {
        return post("/api/v1/deliverable-types", token, typeBody(name, kind, required, maxFiles))
                .expect(201)
                .json("$.id");
    }

    private Map<String, Object> typeBody(String name, String kind, boolean required, int maxFiles) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", TRACK);
        body.put("name", name);
        body.put("description", "Entregable de prueba");
        body.put("kind", kind);
        body.put("required", required);
        body.put("maxFiles", maxFiles);
        body.put("sortOrder", 1);
        return body;
    }

    private Response uploadPdf(int projectId, int typeId, String token) {
        return putOrPostFile(projectId, typeId, token, "poster.pdf", MediaType.APPLICATION_PDF, TestData.PDF);
    }

    private Response putOrPostFile(
            int projectId, int typeId, String token, String filename, MediaType type, byte[] content) {
        return postFile(
                "/api/v1/projects/" + projectId + "/deliverables?deliverableTypeId=" + typeId,
                token,
                "file",
                filename,
                type,
                content);
    }
}
