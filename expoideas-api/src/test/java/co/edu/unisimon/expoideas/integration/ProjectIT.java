package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unisimon.expoideas.catalogs.SectorRepository;
import co.edu.unisimon.expoideas.common.TimeConfig;
import co.edu.unisimon.expoideas.editions.EditionRepository;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.projects.MemberRole;
import co.edu.unisimon.expoideas.projects.MembershipStatus;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectRepository;
import co.edu.unisimon.expoideas.users.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Inscripción de proyectos y equipo contra MySQL: quién inscribe, quién invita,
 * quién acepta y qué pasa cuando la inscripción está cerrada.
 */
class ProjectIT extends IntegrationTest {

    /** Edición que ya terminó, para probar los plazos. */
    private static Integer closedEditionId;

    private static final String TRACK = "INNPRENDE_I";

    /** Una carrera no sale siempre: se repite para que, sin la protección, alguna vuelta falle. */
    private static final int ROUNDS = 5;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EditionRepository editionRepository;

    @Autowired
    private SectorRepository sectorRepository;

    // ── Prerrequisito de INNPRENDE I a II ───────────────────────────────────

    @Test
    void trackTwoRequiresAnApprovedTrackOne() {
        Team team = newTeam();
        Map<String, Object> body = projectBody(team);
        body.put("track", "INNPRENDE_II");
        chooseAnyPrototypeType(body);

        Response refused = post("/api/v1/projects", team.leaderToken(), body).expect(403);
        assertThat(refused.json("$.detail").toString()).contains("aprobado INNPRENDE I");

        // La gestión registra la aprobación a mano (cursó INNPRENDE I antes de la plataforma).
        String macondolab = loginAs(Role.MACONDOLAB);
        Response approval = post(
                        "/api/v1/admin/track-approvals",
                        macondolab,
                        Map.of("userId", idOf(team.leaderEmail()), "track", "INNPRENDE_I"))
                .expect(201);
        assertThat(approval.<Integer>json("$.projectId")).isNull();
        post(
                        "/api/v1/admin/track-approvals",
                        macondolab,
                        Map.of("userId", idOf(team.leaderEmail()), "track", "INNPRENDE_I"))
                .expect(409);
        List<String> tracks = get("/api/v1/admin/track-approvals?userId=" + idOf(team.leaderEmail()), macondolab)
                .expect(200)
                .json("$[*].track");
        assertThat(tracks).containsExactly("INNPRENDE_I");

        int projectId =
                post("/api/v1/projects", team.leaderToken(), body).expect(201).json("$.id");

        // Invitar a alguien sin la aprobación se rechaza en el campo del correo.
        Response invited = post(
                        "/api/v1/projects/" + projectId + "/invitations",
                        team.leaderToken(),
                        Map.of("email", team.memberEmail()))
                .expect(400);
        assertThat(invited.json("$.fields.email").toString()).contains("aprobado INNPRENDE I");

        delete("/api/v1/admin/track-approvals/" + approval.<Integer>json("$.id"), macondolab)
                .expect(204);
        get("/api/v1/admin/track-approvals?userId=" + idOf(team.leaderEmail()), loginAs(Role.TEACHER))
                .expect(403);
    }

    @Test
    void nobodyIsOnBothTracksOfTheSameEdition() {
        Team team = newTeam();
        approveManually(team.leaderEmail());
        approveManually(team.memberEmail());
        createProject(team);

        Map<String, Object> second = projectBody(team);
        second.put("track", "INNPRENDE_II");
        chooseAnyPrototypeType(second);
        Response refused = post("/api/v1/projects", team.leaderToken(), second).expect(409);
        assertThat(refused.json("$.detail").toString()).contains("misma edición");

        // El compañero, que sí puede entrar en II, inscribe ahí e intenta invitar al líder de I.
        int trackTwo =
                post("/api/v1/projects", team.memberToken(), second).expect(201).json("$.id");
        Response invited = post(
                        "/api/v1/projects/" + trackTwo + "/invitations",
                        team.memberToken(),
                        Map.of("email", team.leaderEmail()))
                .expect(400);
        assertThat(invited.json("$.fields.email").toString()).contains("misma edición");
    }

    @Test
    void theTeacherSetsTheResultAfterTheDeadlineAndItOpensTrackTwo() {
        Team team = newTeam();
        String teacher = login(team.teacherEmail(), PASSWORD);
        Map<String, Object> approved = Map.of("result", "APPROVED");

        // Con las entregas abiertas todavía no hay resultado.
        int open = createProject(team);
        Response early =
                put("/api/v1/projects/" + open + "/result", teacher, approved).expect(409);
        assertThat(early.json("$.detail").toString()).contains("después del cierre");

        // Un proyecto de una edición que ya terminó, con el líder, el compañero y un tercero aceptados.
        String third = createAccount(Role.STUDENT);
        int finished = finishedProject(team, third, "Proyecto terminado " + System.nanoTime());
        put("/api/v1/projects/" + finished + "/result", loginAs(Role.TEACHER), approved)
                .expect(404);
        put("/api/v1/projects/" + finished + "/result", team.leaderToken(), approved)
                .expect(403);
        Response result = put("/api/v1/projects/" + finished + "/result", teacher, approved)
                .expect(200);
        assertThat(result.<String>json("$.result")).isEqualTo("APPROVED");

        // Cada integrante aceptado queda con INNPRENDE I aprobada, y ya puede inscribirse en II
        // (el líder no: sigue en el proyecto de I de la edición abierta).
        String macondolab = loginAs(Role.MACONDOLAB);
        List<Integer> projects = get("/api/v1/admin/track-approvals?userId=" + idOf(team.memberEmail()), macondolab)
                .expect(200)
                .json("$[*].projectId");
        assertThat(projects).containsExactly(finished);
        Map<String, Object> trackTwo = projectBody(team);
        trackTwo.put("track", "INNPRENDE_II");
        chooseAnyPrototypeType(trackTwo);
        post("/api/v1/projects", team.memberToken(), trackTwo).expect(201);
        post("/api/v1/projects", team.leaderToken(), trackTwo).expect(409);

        // El listado de la gestión filtra por resultado.
        List<Integer> approvedIds = get("/api/v1/projects?result=APPROVED&size=200", macondolab)
                .expect(200)
                .json("$.items[*].id");
        assertThat(approvedIds).contains(finished).doesNotContain(open);

        // La gestión lo cambia a no aprobado: se retiran las aprobaciones que vinieron de ese proyecto.
        Response reverted = put(
                        "/api/v1/projects/" + finished + "/result", macondolab, Map.of("result", "NOT_APPROVED"))
                .expect(200);
        assertThat(reverted.<String>json("$.result")).isEqualTo("NOT_APPROVED");
        List<Integer> left = get("/api/v1/admin/track-approvals?userId=" + idOf(third), macondolab)
                .expect(200)
                .json("$[*].id");
        assertThat(left).isEmpty();
        post("/api/v1/projects", login(third, PASSWORD), trackTwo).expect(403);
    }

    // ── Inscripción y equipo ────────────────────────────────────────────────

    @Test
    void theLeaderRegistersAProjectAndInvitesAClassmate() {
        Team team = newTeam();

        Response project =
                post("/api/v1/projects", team.leaderToken(), projectBody(team)).expect(201);
        int projectId = project.json("$.id");
        List<String> roles = project.json("$.members[*].teamRole");
        List<String> statuses = project.json("$.members[*].status");
        assertThat(roles).containsExactly("LEADER");
        assertThat(statuses).containsExactly("ACCEPTED");
        assertThat((Integer) project.json("$.maxMembers")).isEqualTo(3);

        post("/api/v1/projects/" + projectId + "/invitations", team.leaderToken(), Map.of("email", team.memberEmail()))
                .expect(201);

        Response invitations = get("/api/v1/invitations", team.memberToken()).expect(200);
        List<String> titles = invitations.json("$[*].projectTitle");
        assertThat(titles).containsExactly("Proyecto de prueba");
        int invitationId = invitations.json("$[0].id");

        Response accepted = post(
                        "/api/v1/invitations/" + invitationId + "/acceptance",
                        team.memberToken(),
                        Map.of("semester", 4))
                .expect(200);
        List<String> acceptedStatuses = accepted.json("$.members[*].status");
        assertThat(acceptedStatuses).containsExactly("ACCEPTED", "ACCEPTED");

        List<Integer> mine =
                get("/api/v1/projects/mine", team.memberToken()).expect(200).json("$[*].id");
        assertThat(mine).containsExactly(projectId);
    }

    @Test
    void anInvitationCanBeDeclinedAndSentAgain() {
        Team team = newTeam();
        int projectId = createProject(team);

        invite(projectId, team, team.memberEmail());
        int invitationId =
                get("/api/v1/invitations", team.memberToken()).expect(200).json("$[0].id");

        post("/api/v1/invitations/" + invitationId + "/rejection", team.memberToken(), null)
                .expect(204);

        List<Integer> pending =
                get("/api/v1/invitations", team.memberToken()).expect(200).json("$[*].id");
        assertThat(pending).isEmpty();

        List<String> team_ = get("/api/v1/projects/" + projectId, team.leaderToken())
                .expect(200)
                .json("$.members[*].teamRole");
        assertThat(team_).containsExactly("LEADER");

        // El líder puede volver a invitar a la misma persona.
        invite(projectId, team, team.memberEmail());
    }

    @Test
    void eachStudentHasOneProjectPerTrack() {
        Team team = newTeam();
        createProject(team);

        post("/api/v1/projects", team.leaderToken(), projectBody(team)).expect(409);
    }

    @Test
    void anAcceptedMemberCannotJoinAnotherTeamOfTheSameTrack() {
        Team first = newTeam();
        int firstProject = createProject(first);
        invite(firstProject, first, first.memberEmail());
        int invitationId =
                get("/api/v1/invitations", first.memberToken()).expect(200).json("$[0].id");
        post("/api/v1/invitations/" + invitationId + "/acceptance", first.memberToken(), Map.of("semester", 4))
                .expect(200);

        Team second = newTeam();
        int secondProject = createProject(second);
        Response rejected = post(
                        "/api/v1/projects/" + secondProject + "/invitations",
                        second.leaderToken(),
                        Map.of("email", first.memberEmail()))
                .expect(400);
        assertThat(rejected.json("$.fields.email").toString()).contains("ya tiene un proyecto");
    }

    @Test
    void theTeamCannotExceedTheMaximum() {
        Team team = newTeam();
        int projectId = createProject(team);

        // El máximo de la cátedra es 3 y el líder ya ocupa un lugar.
        invite(projectId, team, team.memberEmail());
        invite(projectId, team, createAccount(Role.STUDENT));

        Response full = post(
                        "/api/v1/projects/" + projectId + "/invitations",
                        team.leaderToken(),
                        Map.of("email", createAccount(Role.STUDENT)))
                .expect(409);
        assertThat(full.json("$.detail").toString()).contains("máximo de 3");
    }

    // ── Lo que solo se rompe cuando dos peticiones llegan a la vez ──────────

    @Test
    void theDatabaseItselfRefusesASecondAcceptedTeamInTheSameEdition() {
        String student = createAccount(Role.STUDENT);
        String token = login(student, PASSWORD);
        List<Integer> invitations = inviteToTwoProjects(student, token);
        post("/api/v1/invitations/" + invitations.get(0) + "/acceptance", token, Map.of("semester", 4))
                .expect(200);

        // Sin pasar por las comprobaciones de la API, como una petición que llegó en el mismo instante.
        assertThatThrownBy(() ->
                        jdbc.update("UPDATE project_members SET status = 'ACCEPTED' WHERE id = ?", invitations.get(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptingTwoInvitationsAtOnceLeavesTheStudentOnOneTeam() {
        for (int round = 0; round < ROUNDS; round++) {
            String student = createAccount(Role.STUDENT);
            String token = login(student, PASSWORD);
            List<Integer> invitations = inviteToTwoProjects(student, token);

            List<Response> answers = atTheSameTime(
                    () -> post(
                            "/api/v1/invitations/" + invitations.get(0) + "/acceptance", token, Map.of("semester", 4)),
                    () -> post(
                            "/api/v1/invitations/" + invitations.get(1) + "/acceptance", token, Map.of("semester", 4)));

            assertThat(statusesOf(answers)).containsExactlyInAnyOrder(200, 409);
            assertThat(refusedOf(answers).json("$.detail").toString()).contains("Ya tienes un proyecto");
            assertThat(acceptedTeamsOf(student)).isEqualTo(1);
        }
    }

    @Test
    void aDoubleClickOnRegisterCreatesOneProject() {
        for (int round = 0; round < ROUNDS; round++) {
            Team team = newTeam();
            Map<String, Object> body = projectBody(team);

            List<Response> answers = atTheSameTime(
                    () -> post("/api/v1/projects", team.leaderToken(), body),
                    () -> post("/api/v1/projects", team.leaderToken(), body));

            assertThat(statusesOf(answers)).containsExactlyInAnyOrder(201, 409);
            assertThat(refusedOf(answers).json("$.detail").toString()).contains("Ya tienes un proyecto");
            assertThat(acceptedTeamsOf(team.leaderEmail())).isEqualTo(1);
        }
    }

    @Test
    void twoInvitationsAtOnceDoNotExceedTheMaximum() {
        for (int round = 0; round < ROUNDS; round++) {
            Team team = newTeam();
            int projectId = createProject(team);
            // El máximo es 3: con el líder y esta invitación queda un solo lugar.
            invite(projectId, team, team.memberEmail());
            String third = createAccount(Role.STUDENT);
            String fourth = createAccount(Role.STUDENT);
            String uri = "/api/v1/projects/" + projectId + "/invitations";

            List<Response> answers = atTheSameTime(
                    () -> post(uri, team.leaderToken(), Map.of("email", third)),
                    () -> post(uri, team.leaderToken(), Map.of("email", fourth)));

            assertThat(statusesOf(answers)).containsExactlyInAnyOrder(201, 409);
            assertThat(refusedOf(answers).json("$.detail").toString()).contains("máximo de 3");
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM project_members WHERE project_id = ?", Integer.class, projectId))
                    .isEqualTo(3);
        }
    }

    @Test
    void onlyStudentsWithAnAccountAreInvited() {
        Team team = newTeam();
        int projectId = createProject(team);

        Response unknown = post(
                        "/api/v1/projects/" + projectId + "/invitations",
                        team.leaderToken(),
                        Map.of("email", "nadie@unisimon.edu.co"))
                .expect(400);
        assertThat(unknown.json("$.fields.email").toString()).contains("No hay una cuenta");

        Response teacher = post(
                        "/api/v1/projects/" + projectId + "/invitations",
                        team.leaderToken(),
                        Map.of("email", team.teacherEmail()))
                .expect(400);
        assertThat(teacher.json("$.fields.email").toString()).contains("estudiantes");
    }

    // ── Permisos ────────────────────────────────────────────────────────────

    @Test
    void onlyTheLeaderEditsTheProjectAndInvites() {
        Team team = newTeam();
        int projectId = createProject(team);
        invite(projectId, team, team.memberEmail());
        int invitationId =
                get("/api/v1/invitations", team.memberToken()).expect(200).json("$[0].id");
        post("/api/v1/invitations/" + invitationId + "/acceptance", team.memberToken(), Map.of("semester", 4))
                .expect(200);

        Map<String, Object> changed = projectBody(team);
        changed.put("title", "Otro título");
        put("/api/v1/projects/" + projectId, team.memberToken(), changed).expect(403);
        post(
                        "/api/v1/projects/" + projectId + "/invitations",
                        team.memberToken(),
                        Map.of("email", createAccount(Role.STUDENT)))
                .expect(403);

        put("/api/v1/projects/" + projectId, team.leaderToken(), changed).expect(200);
        String title = get("/api/v1/projects/" + projectId, team.memberToken())
                .expect(200)
                .json("$.title");
        assertThat(title).isEqualTo("Otro título");
    }

    @Test
    void theProjectIsVisibleToItsTeacherAndManagementButNotToOtherStudents() {
        Team team = newTeam();
        int projectId = createProject(team);

        get("/api/v1/projects/" + projectId, login(team.teacherEmail(), PASSWORD))
                .expect(200);
        get("/api/v1/projects/" + projectId, loginAs(Role.MACONDOLAB)).expect(200);
        get("/api/v1/projects/" + projectId, loginAs(Role.STUDENT)).expect(404);
        get("/api/v1/projects/" + projectId, null).expect(401);
    }

    @Test
    void nobodyLeavesOnTheirOwnTheLeaderOrManagementRemoveAndItLeavesATrace() {
        Team team = newTeam();
        int projectId = createProject(team);
        String members = "/api/v1/projects/" + projectId + "/members/";
        int memberId = idOf(team.memberEmail());
        int leaderId = idOf(team.leaderEmail());
        joinTeam(projectId, team);

        // Ni el líder ni el integrante se salen por su cuenta.
        delete(members + leaderId, team.leaderToken()).expect(403);
        String detail =
                delete(members + memberId, team.memberToken()).expect(403).json("$.detail");
        assertThat(detail).contains("pídeselo a MacondoLab");
        assertThat(teamRoles(projectId, team)).containsExactly("LEADER", "MEMBER");

        // La gestión lo retira; al líder, tampoco ella.
        String macondolabEmail = createAccount(Role.MACONDOLAB);
        String macondolab = login(macondolabEmail, PASSWORD);
        delete(members + leaderId, macondolab).expect(403);
        delete(members + memberId, macondolab).expect(204);
        assertThat(teamRoles(projectId, team)).containsExactly("LEADER");

        // Y el líder, a quien vuelva a entrar.
        joinTeam(projectId, team);
        delete(members + memberId, team.leaderToken()).expect(204);
        assertThat(teamRoles(projectId, team)).containsExactly("LEADER");

        // Las dos salidas quedan en el rastro, con quién las hizo. De la más reciente a la más antigua.
        List<Map<String, Object>> trail = removalsOf(projectId);
        assertThat(trail).extracting(row -> row.get("actorEmail")).containsExactly(team.leaderEmail(), macondolabEmail);
        assertThat(trail.getFirst())
                .containsEntry("actionLabel", "Integrante retirado del equipo")
                .containsEntry("targetType", "PROJECT");
        assertThat(trail.getFirst().get("detail")).asString().contains(team.memberEmail());
    }

    @Test
    void whoIsOnlyInvitedWithdrawsTheInvitationWithoutATrace() {
        Team team = newTeam();
        int projectId = createProject(team);
        invite(projectId, team, team.memberEmail());

        delete("/api/v1/projects/" + projectId + "/members/" + idOf(team.memberEmail()), team.memberToken())
                .expect(204);

        assertThat(teamRoles(projectId, team)).containsExactly("LEADER");
        assertThat(removalsOf(projectId)).isEmpty();
    }

    @Test
    void theLeaderFindsClassmatesByNameAndInvitesThemWithoutKnowingTheirEmail() {
        Team team = newTeam();
        int projectId = createProject(team);
        String candidates = "/api/v1/projects/" + projectId + "/invitation-candidates?search=";
        // Un apellido que nadie más tiene, para no depender de lo que dejen otras pruebas.
        String surname = "Zuñiga" + System.nanoTime();
        String mauro = named(createAccount(Role.STUDENT), "Mauro", surname);
        String maura = named(createAccount(Role.STUDENT), "Maura", surname);
        // No se ofrecen: no es estudiante, está suspendida, o ya es del equipo.
        named(team.teacherEmail(), "Mauricio", surname);
        String suspended = named(createAccount(Role.STUDENT), "Mauren", surname);
        jdbc.update("update users set enabled = false where email = ?", suspended);
        named(team.leaderEmail(), "Mauricia", surname);

        // Sin mayúsculas ni tildes, y en orden alfabético.
        Response found = get(candidates + "zuniga" + surname.substring(6), team.leaderToken())
                .expect(200);
        assertThat(found.<List<String>>json("$[*].fullName")).containsExactly("Maura " + surname, "Mauro " + surname);
        assertThat(found.<Integer>json("$[1].id")).isEqualTo(idOf(mauro));
        // Lo justo para reconocerlo: de dónde es, y el correo sin poder leerlo entero.
        assertThat(found.<String>json("$[1].faculty")).isNotBlank();
        assertThat(found.<String>json("$[1].email")).isEqualTo(mauro.charAt(0) + "***@unisimon.edu.co");
        assertThat(found.body()).doesNotContain(mauro, maura);
        assertThat(get(candidates + "Mauro " + surname, team.leaderToken())
                        .expect(200)
                        .<List<String>>json("$[*].fullName"))
                .containsExactly("Mauro " + surname);

        // Con menos de tres letras no se busca: saldría media cátedra.
        assertThat(get(candidates + "Ma", team.leaderToken()).expect(200).<List<Object>>json("$"))
                .isEmpty();

        // Solo busca el líder de ese proyecto.
        get(candidates + surname, team.memberToken()).expect(404);
        get(candidates + surname, loginAs(Role.MACONDOLAB)).expect(403);

        // Lo invita por su cuenta, sin escribir el correo, y deja de salir en la búsqueda.
        String invitations = "/api/v1/projects/" + projectId + "/invitations";
        post(invitations, team.leaderToken(), Map.of("userId", idOf(mauro))).expect(201);
        assertThat(get("/api/v1/invitations", login(mauro, PASSWORD))
                        .expect(200)
                        .<List<Integer>>json("$[*].projectId"))
                .containsExactly(projectId);
        assertThat(get(candidates + surname, team.leaderToken()).expect(200).<List<String>>json("$[*].fullName"))
                .containsExactly("Maura " + surname);

        // Las reglas de siempre siguen valiendo por este camino, y sin decir a quién no se invita a nadie.
        Response teacher = post(invitations, team.leaderToken(), Map.of("userId", idOf(team.teacherEmail())))
                .expect(400);
        assertThat(teacher.<String>json("$.fields.email")).isEqualTo("Solo puedes invitar a estudiantes");
        assertThat(post(invitations, team.leaderToken(), Map.of()).expect(400).<String>json("$.fields.email"))
                .contains("elige a tu compañero o escribe su correo");
    }

    @Test
    void whoRegistersSaysTheirSemesterAndPhoneAndWhoJoinsTheirSemester() {
        Team team = newTeam();
        String projects = "/api/v1/projects";

        // Sin semestre o sin celular no hay inscripción, y cada error va a su campo.
        Map<String, Object> incomplete = projectBody(team);
        incomplete.remove("semester");
        incomplete.remove("phone");
        Response missing = post(projects, team.leaderToken(), incomplete).expect(400);
        assertThat(missing.<String>json("$.fields.semester")).contains("del 1 al 12");
        assertThat(missing.<String>json("$.fields.phone")).isEqualTo("El celular es obligatorio");

        Map<String, Object> wrong = projectBody(team);
        wrong.put("semester", 13);
        wrong.put("phone", "300 123");
        Response invalid = post(projects, team.leaderToken(), wrong).expect(400);
        assertThat(invalid.<String>json("$.fields.semester")).contains("del 1 al 12");
        assertThat(invalid.<String>json("$.fields.phone")).contains("10 dígitos");

        Map<String, Object> body = projectBody(team);
        body.put("semester", 7);
        body.put("phone", "3105550101");
        Response created = post(projects, team.leaderToken(), body).expect(201);
        int projectId = created.json("$.id");
        String project = projects + "/" + projectId;
        assertThat(created.<List<Integer>>json("$.members[*].semester")).containsExactly(7);
        assertThat(created.<String>json("$.leaderPhone")).isEqualTo("3105550101");

        // Quien entra dice su semestre al aceptar; mientras no responda, no tiene.
        invite(projectId, team, team.memberEmail());
        assertThat(get(project, team.leaderToken()).expect(200).<List<Integer>>json("$.members[*].semester"))
                .containsExactly(7, null);
        int invitationId =
                get("/api/v1/invitations", team.memberToken()).expect(200).json("$[0].id");
        String acceptance = "/api/v1/invitations/" + invitationId + "/acceptance";
        assertThat(post(acceptance, team.memberToken(), Map.of()).expect(400).<String>json("$.fields.semester"))
                .contains("del 1 al 12");
        post(acceptance, team.memberToken(), Map.of("semester", 0)).expect(400);
        Response accepted =
                post(acceptance, team.memberToken(), Map.of("semester", 3)).expect(200);
        assertThat(accepted.<List<Integer>>json("$.members[*].semester")).containsExactly(7, 3);

        // El celular del líder lo ven él, el profesor del grupo y la gestión. Su compañero y un jurado, no.
        String jurorEmail = createAccount(Role.JUDGE);
        post(project + "/jurors", loginAs(Role.MACONDOLAB), Map.of("email", jurorEmail))
                .expect(201);
        String juror = login(jurorEmail, PASSWORD);
        assertThat(accepted.<Object>json("$.leaderPhone")).isNull();
        assertThat(get(project, team.memberToken()).expect(200).<Object>json("$.leaderPhone"))
                .isNull();
        assertThat(get(project, juror).expect(200).<Object>json("$.leaderPhone"))
                .isNull();
        assertThat(get("/api/v1/jury/projects", juror).expect(200).body()).doesNotContain("3105550101");
        assertThat(get(project, login(team.teacherEmail(), PASSWORD))
                        .expect(200)
                        .<String>json("$.leaderPhone"))
                .isEqualTo("3105550101");
        assertThat(get(project, loginAs(Role.MACONDOLAB)).expect(200).<String>json("$.leaderPhone"))
                .isEqualTo("3105550101");
        assertThat(get("/api/v1/projects/mine", team.memberToken()).expect(200).body())
                .doesNotContain("3105550101");

        // El líder corrige los dos datos al editar el proyecto.
        body.put("semester", 8);
        body.put("phone", "3205550102");
        Response edited = put(project, team.leaderToken(), body).expect(200);
        assertThat(edited.<List<Integer>>json("$.members[*].semester")).containsExactly(8, 3);
        assertThat(edited.<String>json("$.leaderPhone")).isEqualTo("3205550102");

        // Y salen en el listado y en el CSV de la gestión, que es para lo que se piden.
        String macondolab = loginAs(Role.MACONDOLAB);
        Response summary = get("/api/v1/projects?teacherId=" + idOf(team.teacherEmail()), macondolab)
                .expect(200);
        assertThat(summary.<Integer>json("$.items[0].leaderSemester")).isEqualTo(8);
        assertThat(summary.<String>json("$.items[0].leaderPhone")).isEqualTo("3205550102");
        String csv = get("/api/v1/projects/export?teacherId=" + idOf(team.teacherEmail()), macondolab)
                .expect(200)
                .body();
        assertThat(csv).contains("Líder;Semestre del líder;Celular del líder;").contains(";8;3205550102;");
    }

    /** Le pone ese nombre a la cuenta y devuelve su correo. */
    private String named(String email, String firstName, String lastName) {
        jdbc.update("update users set first_name = ?, last_name = ? where email = ?", firstName, lastName, email);
        return email;
    }

    /** El integrante del equipo recibe la invitación del líder y la acepta. */
    private void joinTeam(int projectId, Team team) {
        invite(projectId, team, team.memberEmail());
        int invitationId =
                get("/api/v1/invitations", team.memberToken()).expect(200).json("$[0].id");
        post("/api/v1/invitations/" + invitationId + "/acceptance", team.memberToken(), Map.of("semester", 4))
                .expect(200);
    }

    private List<String> teamRoles(int projectId, Team team) {
        return get("/api/v1/projects/" + projectId, team.leaderToken())
                .expect(200)
                .json("$.members[*].teamRole");
    }

    /** Lo que el rastro guarda de integrantes retirados de ese proyecto, de lo más reciente a lo más antiguo. */
    private List<Map<String, Object>> removalsOf(int projectId) {
        List<Map<String, Object>> rows = get(
                        "/api/v1/admin/audit?action=TEAM_MEMBER_REMOVED&size=100", loginAs(Role.ADMIN))
                .expect(200)
                .json("$.items");
        return rows.stream()
                .filter(row -> Integer.valueOf(projectId).equals(row.get("targetId")))
                .toList();
    }

    @Test
    void theLeaderDeletesTheRegistration() {
        Team team = newTeam();
        int projectId = createProject(team);

        delete("/api/v1/projects/" + projectId, loginAs(Role.STUDENT)).expect(404);
        delete("/api/v1/projects/" + projectId, team.leaderToken()).expect(204);
        get("/api/v1/projects/" + projectId, team.leaderToken()).expect(404);
    }

    // ── Plazos y datos ──────────────────────────────────────────────────────

    @Test
    void nothingIsRegisteredWhileTheEditionIsClosed() {
        Team team = newTeam();
        Map<String, Object> body = projectBody(team);
        body.put("editionId", closedEdition());

        Response closed = post("/api/v1/projects", team.leaderToken(), body).expect(409);
        assertThat(closed.json("$.detail").toString()).contains("no están abiertas");
    }

    @Test
    void theTeacherMustBeATeacherAccount() {
        Team team = newTeam();
        Map<String, Object> body = projectBody(team);
        body.put("teacherId", idOf(team.leaderEmail()));
        body.put("semester", 5);
        body.put("phone", "3001234567");

        Response rejected = post("/api/v1/projects", team.leaderToken(), body).expect(400);
        assertThat(rejected.json("$.fields.teacherId").toString()).contains("profesor");
    }

    @Test
    void teachersAreListedForTheForm() {
        Team team = newTeam();

        List<Integer> ids =
                get("/api/v1/teachers", team.leaderToken()).expect(200).json("$[*].id");
        assertThat(ids).contains(idOf(team.teacherEmail()));

        get("/api/v1/teachers", null).expect(401);
    }

    @Test
    void managementDoesNotRegisterProjects() {
        Team team = newTeam();

        post("/api/v1/projects", loginAs(Role.MACONDOLAB), projectBody(team)).expect(403);
        post("/api/v1/projects", login(team.teacherEmail(), PASSWORD), projectBody(team))
                .expect(403);
    }

    @Test
    void anAccountOnATeamOrTeachingAProjectCannotBeDeleted() {
        Team team = newTeam();
        createProject(team);
        String admin = loginAs(Role.ADMIN);

        Response leader =
                delete("/api/v1/admin/users/" + idOf(team.leaderEmail()), admin).expect(409);
        assertThat(leader.json("$.detail").toString()).contains("está en el equipo");

        Response teacher = delete("/api/v1/admin/users/" + idOf(team.teacherEmail()), admin)
                .expect(409);
        assertThat(teacher.json("$.detail").toString()).contains("es el profesor");

        // Una cuenta sin proyectos se elimina como siempre.
        delete("/api/v1/admin/users/" + idOf(createAccount(Role.STUDENT)), admin)
                .expect(204);
    }

    // ── Datos de apoyo ──────────────────────────────────────────────────────

    /** Líder, compañero y docente recién creados, con sus sesiones. */
    private record Team(
            String leaderEmail, String leaderToken, String memberEmail, String memberToken, String teacherEmail) {}

    private Team newTeam() {
        String leader = createAccount(Role.STUDENT);
        String member = createAccount(Role.STUDENT);
        String teacher = createAccount(Role.TEACHER);
        return new Team(leader, login(leader, PASSWORD), member, login(member, PASSWORD), teacher);
    }

    private Map<String, Object> projectBody(Team team) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", TRACK);
        body.put("title", "Proyecto de prueba");
        body.put("summary", "Una propuesta de valor de prueba para la inscripción.");
        body.put("sectorId", firstSectorId());
        body.put("teacherId", idOf(team.teacherEmail()));
        body.put("semester", 5);
        body.put("phone", "3001234567");
        return body;
    }

    private int createProject(Team team) {
        return post("/api/v1/projects", team.leaderToken(), projectBody(team))
                .expect(201)
                .json("$.id");
    }

    private void invite(int projectId, Team team, String email) {
        post("/api/v1/projects/" + projectId + "/invitations", team.leaderToken(), Map.of("email", email))
                .expect(201);
    }

    /** Dos equipos distintos invitan a la misma persona; devuelve sus dos invitaciones. */
    private List<Integer> inviteToTwoProjects(String student, String token) {
        Team first = newTeam();
        Team second = newTeam();
        invite(createProject(first), first, student);
        invite(createProject(second), second, student);
        return get("/api/v1/invitations", token).expect(200).json("$[*].id");
    }

    private static List<Integer> statusesOf(List<Response> answers) {
        return answers.stream().map(answer -> answer.status().value()).toList();
    }

    private static Response refusedOf(List<Response> answers) {
        return answers.stream()
                .filter(answer -> answer.status().value() == 409)
                .findFirst()
                .orElseThrow();
    }

    private int acceptedTeamsOf(String email) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_members WHERE user_id = ? AND status = 'ACCEPTED'",
                Integer.class,
                idOf(email));
    }

    private void approveManually(String email) {
        post(
                        "/api/v1/admin/track-approvals",
                        loginAs(Role.MACONDOLAB),
                        Map.of("userId", idOf(email), "track", "INNPRENDE_I"))
                .expect(201);
    }

    /**
     * Un proyecto de la edición cerrada, escrito directamente en la base: por la
     * API ya no se puede inscribir nada ahí, que es justo lo que se quiere probar.
     */
    private int finishedProject(Team team, String thirdEmail, String title) {
        LocalDateTime now = LocalDateTime.now(TimeConfig.ZONE);
        Project project = new Project();
        project.setEdition(editionRepository.findWithTracksById(closedEdition()).orElseThrow());
        project.setTrack(Track.INNPRENDE_I);
        project.setTitle(title);
        project.setSummary("Un proyecto que ya terminó.");
        project.setSector(sectorRepository.findAll().getFirst());
        project.setTeacher(userRepository.findByEmail(team.teacherEmail()).orElseThrow());
        project.addMember(
                userRepository.findByEmail(team.leaderEmail()).orElseThrow(),
                MemberRole.LEADER,
                MembershipStatus.ACCEPTED,
                now);
        project.addMember(
                userRepository.findByEmail(team.memberEmail()).orElseThrow(),
                MemberRole.MEMBER,
                MembershipStatus.ACCEPTED,
                now);
        project.addMember(
                userRepository.findByEmail(thirdEmail).orElseThrow(),
                MemberRole.MEMBER,
                MembershipStatus.ACCEPTED,
                now);
        return projectRepository.save(project).getId();
    }

    private int firstSectorId() {
        List<Integer> ids = get("/api/v1/sectors", null).expect(200).json("$[*].id");
        return ids.getFirst();
    }

    private int closedEdition() {
        if (closedEditionId == null) {
            closedEditionId = createEdition(
                    "Expoideas cerrada " + System.nanoTime(),
                    LocalDate.of(2020, 1, 1),
                    LocalDate.of(2020, 2, 1),
                    LocalDate.of(2020, 3, 1));
        }
        return closedEditionId;
    }

    private int createEdition(String name, LocalDate opens, LocalDate registrationCloses, LocalDate submissionCloses) {
        return post(
                        "/api/v1/editions",
                        loginAs(Role.MACONDOLAB),
                        Map.of(
                                "name",
                                name,
                                "registrationOpensOn",
                                opens.toString(),
                                "registrationClosesOn",
                                registrationCloses.toString(),
                                "submissionClosesOn",
                                submissionCloses.toString(),
                                "tracks",
                                List.of(
                                        Map.of("track", "INNPRENDE_I", "minMembers", 1, "maxMembers", 3),
                                        Map.of("track", "INNPRENDE_II", "minMembers", 2, "maxMembers", 4))))
                .expect(201)
                .json("$.id");
    }
}
