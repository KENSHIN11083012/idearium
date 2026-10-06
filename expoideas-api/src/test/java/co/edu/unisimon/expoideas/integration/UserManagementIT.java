package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Gestión de cuentas: MacondoLab gestiona estudiantes, docentes y jurados; las
 * cuentas de gestión y el borrado quedan para el administrador.
 */
class UserManagementIT extends IntegrationTest {

    @Test
    void theAccountsComeInPagesAndTheDatabaseDoesTheSearching() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String batch = "pagina" + System.nanoTime();
        String first = uniqueEmail(batch + ".a");
        String second = uniqueEmail(batch + ".b");
        String third = uniqueEmail(batch + ".c");
        for (String email : List.of(first, second, third)) {
            register(email).expect(201);
        }
        // Una de las tres pasa a jurado y otra queda con un rol del listado por confirmar.
        put("/api/v1/admin/users/" + idOf(second), macondolab, Map.of("role", "JUDGE"))
                .expect(200);
        jdbc.update("UPDATE users SET pending_role = 'TEACHER' WHERE email = ?", third);
        String accounts = "/api/v1/admin/users?search=" + batch;

        // Las más recientes primero.
        Response page = get(accounts + "&size=2", macondolab).expect(200);
        assertThat(page.<List<String>>json("$.items[*].email")).containsExactly(third, second);
        assertThat(page.<Integer>json("$.totalItems")).isEqualTo(3);
        assertThat(page.<Integer>json("$.totalPages")).isEqualTo(2);
        assertThat(get(accounts + "&size=2&page=1", macondolab).expect(200).<List<String>>json("$.items[*].email"))
                .containsExactly(first);

        assertThat(get(accounts + "&role=JUDGE", macondolab).expect(200).<List<String>>json("$.items[*].email"))
                .containsExactly(second);
        assertThat(get(accounts + "&pendingRole=true", macondolab).expect(200).<List<String>>json("$.items[*].email"))
                .containsExactly(third);

        // Busca en el nombre igual que en el correo, sin distinguir mayúsculas ni tildes.
        jdbc.update("UPDATE users SET first_name = 'Ángela', last_name = ? WHERE email = ?", batch, first);
        assertThat(get("/api/v1/admin/users?search=ANGELA " + batch, macondolab)
                        .expect(200)
                        .<List<String>>json("$.items[*].email"))
                .containsExactly(first);

        get("/api/v1/admin/users?role=NO_EXISTE", macondolab).expect(400);
    }

    @Test
    void managementListsUsersWithAffiliation() {
        String admin = loginAs(Role.ADMIN);
        int faculty = createFaculty(admin);
        String email = uniqueEmail("listado");
        register(email).expect(201);
        completeProfile(login(email, PASSWORD), campusId(), faculty, createProgram(admin, faculty))
                .expect(200);

        Response list = get("/api/v1/admin/users?search=" + email, loginAs(Role.MACONDOLAB))
                .expect(200);
        List<String> programs = list.json("$.items[?(@.email == '" + email + "')].academicProgram");
        assertThat(programs).singleElement().asString().startsWith("Programa ");
    }

    @Test
    void onlyManagementRolesReachAdminRoutes() {
        get("/api/v1/admin/users", loginAs(Role.STUDENT)).expect(403);
        get("/api/v1/admin/users", loginAs(Role.TEACHER)).expect(403);
        get("/api/v1/admin/users", loginAs(Role.JUDGE)).expect(403);
        get("/api/v1/admin/users", loginAs(Role.MACONDOLAB)).expect(200);
        get("/api/v1/admin/users", loginAs(Role.ADMIN)).expect(200);
    }

    @Test
    void resettingAPasswordClosesThatAccountsSessions() {
        String email = createAccount(Role.STUDENT);
        String session = login(email, PASSWORD);
        get("/api/v1/users/me", session).expect(200);

        post(
                        "/api/v1/admin/users/" + idOf(email) + "/password-reset",
                        loginAs(Role.MACONDOLAB),
                        Map.of("newPassword", "Temporal#2026", "confirmPassword", "Temporal#2026"))
                .expect(204);

        // Se restablece porque la cuenta pudo quedar en otras manos: lo que estuviera abierto se cierra.
        get("/api/v1/users/me", session).expect(401);
    }

    @Test
    void resettingAPasswordAlsoLiftsTheLockOfFailedAttempts() {
        String email = createAccount(Role.STUDENT);
        for (int attempt = 0; attempt < 5; attempt++) {
            post("/api/v1/auth/login", null, Map.of("email", email, "password", "Incorrecta#" + attempt))
                    .expect(401);
        }
        post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .expect(429);

        post(
                        "/api/v1/admin/users/" + idOf(email) + "/password-reset",
                        loginAs(Role.MACONDOLAB),
                        Map.of("newPassword", "Temporal#2026", "confirmPassword", "Temporal#2026"))
                .expect(204);

        // Quien olvidó la contraseña y se bloqueó probando no tiene que esperar además el bloqueo.
        login(email, "Temporal#2026");
    }

    @Test
    void aSuspendedAccountLosesItsSessionAndCannotLogInUntilReactivated() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String email = createAccount(Role.STUDENT);
        String session = login(email, PASSWORD);
        String account = "/api/v1/admin/users/" + idOf(email);

        Response suspended = post(account + "/suspension", macondolab, null).expect(200);
        assertThat(suspended.<Boolean>json("$.suspended")).isTrue();

        get("/api/v1/users/me", session).expect(401);
        Response refused = post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .expect(401);
        assertThat(refused.<String>json("$.detail")).contains("suspendida");

        Response reactivated = post(account + "/reactivation", macondolab, null).expect(200);
        assertThat(reactivated.<Boolean>json("$.suspended")).isFalse();

        // Vuelve a entrar con su contraseña, pero la sesión que tenía abierta no revive.
        get("/api/v1/users/me", login(email, PASSWORD)).expect(200);
        get("/api/v1/users/me", session).expect(401);
    }

    @Test
    void suspendingFollowsTheSameRulesAsTheRestOfTheManagement() {
        String macondolabEmail = createAccount(Role.MACONDOLAB);
        String macondolab = login(macondolabEmail, PASSWORD);
        String adminEmail = createAccount(Role.ADMIN);

        // MacondoLab no toca cuentas de gestión, nadie se suspende a sí mismo y el resto no llega a la ruta.
        post("/api/v1/admin/users/" + idOf(adminEmail) + "/suspension", macondolab, null)
                .expect(403);
        post("/api/v1/admin/users/" + idOf(macondolabEmail) + "/suspension", macondolab, null)
                .expect(403);
        post("/api/v1/admin/users/" + idOf(createAccount(Role.STUDENT)) + "/suspension", loginAs(Role.TEACHER), null)
                .expect(403);
        post("/api/v1/admin/users/999999/suspension", macondolab, null).expect(404);
    }

    @Test
    void onlyAdminCreatesManagementAccounts() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String admin = loginAs(Role.ADMIN);

        post("/api/v1/admin/users", macondolab, newAccount(uniqueEmail("gestion"), "ADMIN"))
                .expect(403);
        post("/api/v1/admin/users", macondolab, newAccount(uniqueEmail("gestion"), "MACONDOLAB"))
                .expect(403);
        post("/api/v1/admin/users", admin, newAccount(uniqueEmail("gestion"), "MACONDOLAB"))
                .expect(201);
    }

    @Test
    void onlyJudgesMayHaveExternalEmail() {
        String macondolab = loginAs(Role.MACONDOLAB);

        post("/api/v1/admin/users", macondolab, newAccount("externo" + System.nanoTime() + "@empresa.com", "JUDGE"))
                .expect(201);
        Response rejected = post(
                        "/api/v1/admin/users",
                        macondolab,
                        newAccount("externo" + System.nanoTime() + "@empresa.com", "STUDENT"))
                .expect(400);
        assertThat(rejected.<String>json("$.fields.email")).isNotBlank();
    }

    @Test
    void duplicateEmailIs409() {
        String admin = loginAs(Role.ADMIN);
        String email = createAccount(Role.STUDENT);
        post("/api/v1/admin/users", admin, newAccount(email, "JUDGE")).expect(409);
    }

    @Test
    void roleChangesFollowManagementRules() {
        String macondolab = loginAs(Role.MACONDOLAB);
        int student = idOf(createAccount(Role.STUDENT));
        int admin = idOf(createAccount(Role.ADMIN));

        Response changed = put("/api/v1/admin/users/" + student, macondolab, Map.of("role", "JUDGE"))
                .expect(200);
        assertThat(changed.<String>json("$.role")).isEqualTo("JUDGE");

        put("/api/v1/admin/users/" + student, macondolab, Map.of("role", "ADMIN"))
                .expect(403);
        put("/api/v1/admin/users/" + admin, macondolab, Map.of("role", "STUDENT"))
                .expect(403);
    }

    @Test
    void nobodyChangesOwnRole() {
        String email = createAccount(Role.ADMIN);
        put("/api/v1/admin/users/" + idOf(email), login(email, PASSWORD), Map.of("role", "STUDENT"))
                .expect(403);
    }

    @Test
    void onlyAdminDeletesAccountsButNotItself() {
        String adminEmail = createAccount(Role.ADMIN);
        String admin = login(adminEmail, PASSWORD);
        int student = idOf(createAccount(Role.STUDENT));

        delete("/api/v1/admin/users/" + student, loginAs(Role.MACONDOLAB)).expect(403);
        delete("/api/v1/admin/users/" + idOf(adminEmail), admin).expect(403);

        delete("/api/v1/admin/users/" + student, admin).expect(204);
        assertThat(userRepository.findById(student)).isEmpty();
        delete("/api/v1/admin/users/" + student, admin).expect(404);
    }

    private static Map<String, Object> newAccount(String email, String role) {
        return Map.of(
                "firstName", "Cuenta", "lastName", "Nueva", "email", email, "password", "Temporal#2026", "role", role);
    }

    @Test
    void aPageFarBeyondTheLastOneIsEmptyNotAnError() {
        String admin = loginAs(Role.ADMIN);

        for (String list : List.of("/api/v1/admin/users", "/api/v1/projects", "/api/v1/admin/audit")) {
            Response page = get(list + "?page=2147483647&size=200", admin).expect(200);
            assertThat(page.<List<Object>>json("$.items")).as(list).isEmpty();
        }
    }
}
