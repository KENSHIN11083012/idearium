package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Primer ingreso: una cuenta con contraseña puesta por la gestión queda bloqueada
 * (403 con los pasos pendientes) hasta cambiarla y autorizar el tratamiento de datos.
 */
class OnboardingIT extends IntegrationTest {

    private static final String TEMPORAL = "Temporal#2026";
    private static final String DEFINITIVA = "Definitiva#2026";

    @Test
    void accountCreatedByManagementCompletesOnboarding() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String email = uniqueEmail("JUDGE");

        Response created = post(
                        "/api/v1/admin/users",
                        macondolab,
                        Map.of(
                                "firstName",
                                "Jurado",
                                "lastName",
                                "Externo",
                                "email",
                                email,
                                "password",
                                TEMPORAL,
                                "role",
                                "JUDGE"))
                .expect(201);
        assertThat(created.<List<String>>json("$.pendingSteps")).containsExactly("CHANGE_PASSWORD", "DATA_CONSENT");

        Response login = post("/api/v1/auth/login", null, Map.of("email", email, "password", TEMPORAL))
                .expect(200);
        assertThat(login.<List<String>>json("$.pendingSteps")).containsExactly("CHANGE_PASSWORD", "DATA_CONSENT");
        String token = login.json("$.token");

        // Permitido mientras tanto: ver y completar su perfil y leer catálogos públicos.
        get("/api/v1/users/me", token).expect(200);
        put("/api/v1/users/me", token, Map.of("firstName", "Jurado", "lastName", "Externo"))
                .expect(200);
        get("/api/v1/campuses", token).expect(200);

        // Cualquier otra cosa: 403 con los pasos que faltan.
        Response blocked = delete("/api/v1/users/me/photo", token).expect(403);
        assertThat(blocked.<List<String>>json("$.pendingSteps")).containsExactly("CHANGE_PASSWORD", "DATA_CONSENT");

        // Cambiar la contraseña cierra la sesión con la que se entró: se sigue con el token de la respuesta.
        token = put(
                        "/api/v1/users/me/password",
                        token,
                        Map.of("currentPassword", TEMPORAL, "newPassword", DEFINITIVA, "confirmPassword", DEFINITIVA))
                .expect(200)
                .json("$.token");
        Response stillBlocked = delete("/api/v1/users/me/photo", token).expect(403);
        assertThat(stillBlocked.<List<String>>json("$.pendingSteps")).containsExactly("DATA_CONSENT");

        put("/api/v1/users/me/data-consent", token, Map.of("dataConsent", false))
                .expect(400);
        put("/api/v1/users/me/data-consent", token, Map.of("dataConsent", true)).expect(204);

        delete("/api/v1/users/me/photo", token).expect(204);
        Response again = post("/api/v1/auth/login", null, Map.of("email", email, "password", DEFINITIVA))
                .expect(200);
        assertThat(again.<List<String>>json("$.pendingSteps")).isEmpty();
    }

    @Test
    void passwordResetByManagementIsTemporary() {
        String admin = loginAs(Role.ADMIN);
        String email = createAccount(Role.STUDENT);

        post(
                        "/api/v1/admin/users/" + idOf(email) + "/password-reset",
                        admin,
                        Map.of("newPassword", TEMPORAL, "confirmPassword", TEMPORAL))
                .expect(204);

        post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .expect(401);
        Response login = post("/api/v1/auth/login", null, Map.of("email", email, "password", TEMPORAL))
                .expect(200);
        assertThat(login.<List<String>>json("$.pendingSteps")).containsExactly("CHANGE_PASSWORD");
    }

    @Test
    void managementCannotResetOwnPasswordThroughAdminRoute() {
        String email = createAccount(Role.ADMIN);
        String admin = login(email, PASSWORD);

        post(
                        "/api/v1/admin/users/" + idOf(email) + "/password-reset",
                        admin,
                        Map.of("newPassword", TEMPORAL, "confirmPassword", TEMPORAL))
                .expect(403);
    }

    @Test
    void resettingAMissingAccountIs404() {
        post(
                        "/api/v1/admin/users/999999/password-reset",
                        loginAs(Role.ADMIN),
                        Map.of("newPassword", TEMPORAL, "confirmPassword", TEMPORAL))
                .expect(404);
    }

    @Test
    void teacherCreatedByManagementNeedsAffiliation() {
        String admin = loginAs(Role.ADMIN);
        Map<String, Object> body = new HashMap<>(Map.of(
                "firstName",
                "Docente",
                "lastName",
                "Nuevo",
                "email",
                uniqueEmail("TEACHER"),
                "password",
                TEMPORAL,
                "role",
                "TEACHER"));

        Response missing = post("/api/v1/admin/users", admin, body).expect(400);
        assertThat(missing.<String>json("$.fields.campusId")).isNotBlank();
        assertThat(missing.<String>json("$.fields.facultyId")).isNotBlank();

        body.put("campusId", campusId());
        body.put("facultyId", createFaculty(admin));
        post("/api/v1/admin/users", admin, body).expect(201);
    }
}
