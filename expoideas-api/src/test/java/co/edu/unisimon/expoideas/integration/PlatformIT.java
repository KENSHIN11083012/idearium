package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.notifications.MailFailureRecorder;
import co.edu.unisimon.expoideas.users.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;

/**
 * Arranque completo: si el contexto sube, Flyway aplicó todas las migraciones y
 * el mapeo de Hibernate cuadra con el esquema (ddl-auto=validate).
 */
class PlatformIT extends IntegrationTest {

    @Autowired
    private MailFailureRecorder failures;

    @Test
    void healthResponds() {
        get("/actuator/health", null).expect(200);
        assertThat(get("/actuator/health", null).<String>json("$.status")).isEqualTo("UP");
        get("/actuator/health/liveness", null).expect(200);
        get("/actuator/health/readiness", null).expect(200);
    }

    @Test
    void healthHidesDetails() {
        assertThat(get("/actuator/health", null).body()).doesNotContain("components", "db");
    }

    @Test
    void infoSaysWhichVersionIsRunningAndNothingElse() {
        // Sin sesión: lo consulta quien opera el servidor. Fuera de una imagen publicada, la versión es «local».
        Response info = get("/actuator/info", null).expect(200);
        assertThat(info.<String>json("$.app.name")).isEqualTo("Idearium");
        assertThat(info.<String>json("$.app.version")).isEqualTo("local");
        assertThat(info.body()).doesNotContain("java", "os", "git", "password", "secret");
    }

    @Test
    void anAdminSeesTheStateOfTheInstallation() {
        String admin = loginAs(Role.ADMIN);

        Response status = get("/api/v1/admin/status", admin).expect(200);
        assertThat(status.<String>json("$.version")).isEqualTo("local");
        assertThat(status.<Number>json("$.disk.totalBytes").longValue()).isPositive();
        assertThat(status.<Boolean>json("$.disk.low")).isFalse();
        // Las pruebas corren con un servidor de correo y una URL pública.
        assertThat(status.<Boolean>json("$.mail.configured")).isTrue();
        assertThat(status.<Boolean>json("$.mail.linksEnabled")).isTrue();
        int lostBefore = status.<Integer>json("$.mail.failedLast24Hours");

        // Un correo que no salió, como lo deja NotificationListener tras el último intento.
        failures.record(uniqueEmail("estado"), "Tu cuenta en Idearium", 3, new MailSendException("SMTP caído"));

        Response after = get("/api/v1/admin/status", admin).expect(200);
        assertThat(after.<Integer>json("$.mail.failedLast24Hours")).isEqualTo(lostBefore + 1);
        assertThat(after.<String>json("$.mail.lastFailureAt")).isNotNull();

        // Es de quien opera la instalación: ni MacondoLab ni nadie sin sesión.
        get("/api/v1/admin/status", loginAs(Role.MACONDOLAB)).expect(403);
        get("/api/v1/admin/status", null).expect(401);
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        assertThat(get("/actuator/env", null).status().is2xxSuccessful()).isFalse();
        assertThat(get("/actuator/beans", null).status().is2xxSuccessful()).isFalse();
    }

    @Test
    void campusesAreSeededWithAccents() {
        List<String> names = get("/api/v1/campuses", null).expect(200).json("$[*].name");
        assertThat(names).contains("Barranquilla", "Cúcuta");
    }

    @Test
    void protectedRouteWithoutSessionIs401ProblemDetails() {
        Response response = get("/api/v1/users/me", null).expect(401);
        assertThat(response.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.<Integer>json("$.status")).isEqualTo(401);
    }

    @Test
    void corsPreflightAllowsTheFrontendOriginWithoutCredentials() {
        Response allowed = preflight("http://localhost:5173").expect(200);
        assertThat(allowed.headers().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(allowed.headers().getAccessControlAllowMethods()).contains(HttpMethod.PUT, HttpMethod.DELETE);
        assertThat(allowed.headers().getAccessControlAllowCredentials()).isFalse();

        preflight("https://otro-sitio.example").expect(403);
    }

    private Response preflight(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.PUT);
        headers.setAccessControlRequestHeaders(List.of("authorization", "content-type"));
        return send(HttpMethod.OPTIONS, "/api/v1/users/me", null, null, headers);
    }

    @Test
    void openApiDocumentDescribesTheApi() {
        Response docs = get("/v3/api-docs", null).expect(200);
        assertThat(docs.<Object>json("$.paths['/api/v1/users/me']")).isNotNull();
        assertThat(docs.<Object>json("$.paths['/api/v1/admin/users/{id}/password-reset']"))
                .isNotNull();
        // Las lecturas públicas no piden el token en la documentación.
        assertThat(docs.<List<Object>>json("$.paths['/api/v1/campuses'].get.security"))
                .isEmpty();
    }

    @Test
    void invalidTokenIs401() {
        get("/api/v1/users/me", "no-es-un-jwt").expect(401);
    }
}
