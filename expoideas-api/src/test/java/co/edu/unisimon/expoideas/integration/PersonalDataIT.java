package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Lo que pide la Ley 1581 contra MySQL: qué texto autorizó cada quien, el
 * archivo con los datos propios y la anonimización de una cuenta con historia.
 */
class PersonalDataIT extends IntegrationTest {

    private static final String EXPORT = "/api/v1/users/me/data-export";

    @Test
    void whoRegistersLeavesWhichConsentTextTheyAccepted() {
        String email = uniqueEmail("consiente");
        register(email).expect(201);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT data_consent, data_consent_at, data_consent_version FROM users WHERE email = ?", email);
        assertThat(row.get("data_consent_at")).isNotNull();
        assertThat(row.get("data_consent_version")).isEqualTo("1");
    }

    @Test
    void anyoneDownloadsTheirOwnDataAndOnlyTheirs() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String teacherEmail = createAccount(Role.TEACHER);
        String leaderEmail = createAccount(Role.STUDENT);
        String leader = login(leaderEmail, PASSWORD);
        String title = "Proyecto con datos " + System.nanoTime();
        int projectId = createProject(leader, teacherEmail, title);
        post("/api/v1/projects/" + projectId + "/jurors", macondolab, Map.of("email", teacherJuror()))
                .expect(201);
        // Algo que la gestión le hizo a su cuenta también es un dato suyo.
        post("/api/v1/admin/users/" + idOf(leaderEmail) + "/suspension", macondolab, null)
                .expect(200);
        post("/api/v1/admin/users/" + idOf(leaderEmail) + "/reactivation", macondolab, null)
                .expect(200);
        leader = login(leaderEmail, PASSWORD);

        Response export = get(EXPORT, leader).expect(200);

        assertThat(export.headers().getFirst("Content-Disposition"))
                .startsWith("attachment; filename=\"mis-datos-idearium-")
                .endsWith(".json\"");
        assertThat(export.<String>json("$.account.email")).isEqualTo(leaderEmail);
        assertThat(export.<String>json("$.account.role")).isEqualTo("Estudiante");
        assertThat(export.<String>json("$.account.firstName")).isEqualTo("Prueba");
        assertThat(export.<Boolean>json("$.account.dataConsent")).isTrue();
        assertThat(export.<List<String>>json("$.teams[*].project")).containsExactly(title);
        assertThat(export.<String>json("$.teams[0].teamRole")).isEqualTo("LEADER");
        assertThat(export.<String>json("$.teams[0].status")).isEqualTo("ACCEPTED");
        // Lo que dijo al inscribir también es un dato suyo.
        assertThat(export.<Integer>json("$.teams[0].semester")).isEqualTo(5);
        assertThat(export.<String>json("$.teams[0].phone")).isEqualTo("3001234567");
        assertThat(export.<List<String>>json("$.accountHistory[*].action"))
                .containsExactly("Cuenta suspendida", "Cuenta reactivada");
        // Lo que no tiene también se dice: secciones vacías, no ausentes.
        assertThat(export.<List<Object>>json("$.projectsAsTeacher")).isEmpty();
        assertThat(export.<List<Object>>json("$.juryAssignments")).isEmpty();
        assertThat(export.<List<Object>>json("$.evaluations")).isEmpty();
        assertThat(export.<List<Object>>json("$.deliverables")).isEmpty();
        // Ni la contraseña ni quién le suspendió la cuenta salen en el archivo.
        assertThat(export.body()).doesNotContain("passwordHash", "$2a$", "actor");

        // El profesor del grupo ve lo suyo: el proyecto a su cargo, no el equipo de otro.
        Response teachers = get(EXPORT, login(teacherEmail, PASSWORD)).expect(200);
        assertThat(teachers.<List<String>>json("$.projectsAsTeacher[*].project"))
                .containsExactly(title);
        assertThat(teachers.<List<Object>>json("$.teams")).isEmpty();
        assertThat(teachers.body()).doesNotContain(leaderEmail);

        get(EXPORT, null).expect(401);
    }

    @Test
    void aJurorsDataIncludesWhatTheyGraded() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String title = "Proyecto calificado " + System.nanoTime();
        int projectId = createProject(loginAs(Role.STUDENT), createAccount(Role.TEACHER), title);
        String jurorEmail = createAccount(Role.JUDGE);
        String juror = login(jurorEmail, PASSWORD);
        post("/api/v1/projects/" + projectId + "/jurors", macondolab, Map.of("email", jurorEmail))
                .expect(201);
        put("/api/v1/projects/" + projectId + "/evaluations/mine", juror, Map.of("absent", true))
                .expect(200);

        Response export = get(EXPORT, juror).expect(200);

        assertThat(export.<List<String>>json("$.juryAssignments[*].project")).containsExactly(title);
        assertThat(export.<List<String>>json("$.evaluations[*].project")).containsExactly(title);
        assertThat(export.<Boolean>json("$.evaluations[0].absent")).isTrue();
        assertThat(export.<Double>json("$.evaluations[0].grade")).isEqualTo(0.0);
    }

    @Test
    void anAccountWithHistoryIsAnonymizedInsteadOfDeleted() {
        String admin = loginAs(Role.ADMIN);
        String macondolab = loginAs(Role.MACONDOLAB);
        String teacherEmail = createAccount(Role.TEACHER);
        String leaderEmail = createAccount(Role.STUDENT);
        int leaderId = idOf(leaderEmail);
        String leader = login(leaderEmail, PASSWORD);
        String title = "Proyecto de quien se va " + System.nanoTime();
        int projectId = createProject(leader, teacherEmail, title);
        String account = "/api/v1/admin/users/" + leaderId;
        // Con rastro a su nombre, una fila en el listado de la cátedra y un enlace pendiente.
        put(account, macondolab, Map.of("role", "STUDENT")).expect(200);
        post(account + "/suspension", macondolab, null).expect(200);
        post(account + "/reactivation", macondolab, null).expect(200);
        jdbc.update(
                "INSERT INTO roster_entries (email, role, first_name, last_name, created_at, updated_at)"
                        + " VALUES (?, 'STUDENT', 'Prueba', 'STUDENT', NOW(), NOW())",
                leaderEmail);
        leader = login(leaderEmail, PASSWORD);

        // Está en un equipo: no se puede eliminar. Y anonimizar no es cosa de MacondoLab.
        delete(account, admin).expect(409);
        post(account + "/anonymization", macondolab, null).expect(403);

        Response anonymized = post(account + "/anonymization", admin, null).expect(200);

        String alias = "anonimo-" + leaderId + "@anonimo.invalid";
        assertThat(anonymized.<Boolean>json("$.anonymized")).isTrue();
        assertThat(anonymized.<String>json("$.email")).isEqualTo(alias);
        assertThat(anonymized.<Object>json("$.firstName")).isNull();
        assertThat(anonymized.<Boolean>json("$.suspended")).isTrue();

        // El equipo sigue en pie, pero ya sin el celular que dio al inscribir: decía de quién era.
        assertThat(jdbc.queryForObject(
                        "select count(*) from project_members where user_id = ? and phone is not null",
                        Integer.class,
                        leaderId))
                .isZero();
        assertThat(get("/api/v1/projects/" + projectId, macondolab).expect(200).<Object>json("$.leaderPhone"))
                .isNull();
        assertThat(get("/api/v1/projects/" + projectId, macondolab)
                        .expect(200)
                        .<List<Integer>>json("$.members[*].semester"))
                .containsExactly(5);

        // Ya no entra, ni con la sesión que tenía ni con su contraseña.
        get("/api/v1/users/me", leader).expect(401);
        post("/api/v1/auth/login", null, Map.of("email", leaderEmail, "password", PASSWORD))
                .expect(401);
        post("/api/v1/auth/login", null, Map.of("email", alias, "password", PASSWORD))
                .expect(401);

        // Su proyecto sigue ahí, con un líder que ya no dice quién era.
        Response project = get("/api/v1/projects/" + projectId, macondolab).expect(200);
        assertThat(project.<String>json("$.title")).isEqualTo(title);
        assertThat(project.body()).contains("Cuenta anonimizada").doesNotContain(leaderEmail);

        // El correo no queda en ninguna tabla: ni en la cuenta, ni en el listado, ni en el rastro.
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", leaderEmail))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM roster_entries WHERE email = ?", leaderEmail))
                .isZero();
        assertThat(count(
                        "SELECT COUNT(*) FROM audit_events WHERE actor_email = ? OR target_label = ? OR detail LIKE CONCAT('%', ?, '%')",
                        leaderEmail, leaderEmail, leaderEmail))
                .isZero();
        // El rastro conserva qué se le hizo a esa cuenta, ahora a nombre del alias, y que se anonimizó.
        List<String> trail = jdbc.queryForList(
                "SELECT action FROM audit_events WHERE target_type = 'ACCOUNT' AND target_id = ? AND target_label = ?"
                        + " ORDER BY id",
                String.class,
                leaderId,
                alias);
        assertThat(trail).containsExactly("ACCOUNT_SUSPENDED", "ACCOUNT_REACTIVATED", "ACCOUNT_ANONYMIZED");

        // Ya no es de nadie: no se le cambia nada ni se le devuelve el acceso.
        post(account + "/anonymization", admin, null).expect(409);
        post(account + "/reactivation", admin, null).expect(409);
        put(account, admin, Map.of("role", "JUDGE")).expect(409);

        // En la lista de cuentas aparece como lo que es, y con el correo viejo ya no se la encuentra.
        Response found = get("/api/v1/admin/users?search=" + alias, admin).expect(200);
        assertThat(found.<List<Boolean>>json("$.items[*].anonymized")).containsExactly(true);
        assertThat(get("/api/v1/admin/users?search=" + leaderEmail, admin)
                        .expect(200)
                        .<Integer>json("$.totalItems"))
                .isZero();
    }

    @Test
    void anAnonymizedTeacherIsNoLongerOfferedToNewProjects() {
        String admin = loginAs(Role.ADMIN);
        String teacherEmail = createAccount(Role.TEACHER);
        int teacherId = idOf(teacherEmail);
        String student = loginAs(Role.STUDENT);
        assertThat(get("/api/v1/teachers", student).expect(200).<List<Integer>>json("$[*].id"))
                .contains(teacherId);

        post("/api/v1/admin/users/" + teacherId + "/anonymization", admin, null).expect(200);

        assertThat(get("/api/v1/teachers", student).expect(200).<List<Integer>>json("$[*].id"))
                .doesNotContain(teacherId);
    }

    /** Una cuenta de profesor que puede ser jurado de proyectos de otros. */
    private String teacherJuror() {
        return createAccount(Role.TEACHER);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @SuppressWarnings("unchecked")
    private int createProject(String leaderToken, String teacherEmail, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", "INNPRENDE_I");
        body.put("title", title);
        body.put("summary", "Una propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(teacherEmail));
        body.put("semester", 5);
        body.put("phone", "3001234567");
        return post("/api/v1/projects", leaderToken, body).expect(201).json("$.id");
    }
}
