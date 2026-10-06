package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Lo que una cuenta resuelve con un enlace que le llega al correo: verificar que
 * el correo es suyo al registrarse y poner una contraseña nueva si la olvidó.
 */
class AccountLinkIT extends IntegrationTest {

    private static final String VERIFY = "Verifica tu correo";
    private static final String RECOVER = "Recupera tu contraseña";

    // ── Verificación del correo ─────────────────────────────────────────────

    @Test
    void aRegisteredAccountOnlyWorksAfterOpeningTheLinkSentToItsEmail() throws Exception {
        String email = uniqueEmail("verifica");

        Response created = register(email).expect(201);
        assertThat(created.<List<String>>json("$.pendingSteps")).containsExactly("VERIFY_EMAIL", "COMPLETE_PROFILE");

        // Entra, pero mientras no verifique solo puede resolver su primer ingreso.
        String token = login(email, PASSWORD);
        Response blocked = get("/api/v1/projects/mine", token).expect(403);
        assertThat(blocked.<List<String>>json("$.pendingSteps")).contains("VERIFY_EMAIL");

        MimeMessage mail = awaitMail(email, VERIFY);
        assertThat(body(mail)).contains("http://localhost:5173/expoideas/verificar-correo#token=");
        String link = linkTokenFor(email, VERIFY);

        post("/api/v1/auth/email-verification", null, Map.of("token", link)).expect(204);
        Response me = get("/api/v1/users/me", token).expect(200);
        assertThat(me.<List<String>>json("$.pendingSteps")).containsExactly("COMPLETE_PROFILE");

        // El enlace sirve una sola vez.
        Response again = post("/api/v1/auth/email-verification", null, Map.of("token", link))
                .expect(409);
        assertThat(again.<String>json("$.detail")).contains("no es válido o ya venció");
    }

    @Test
    void aMadeUpTokenVerifiesNothing() {
        post("/api/v1/auth/email-verification", null, Map.of("token", "no-es-un-token-de-verdad"))
                .expect(409);
        post("/api/v1/auth/email-verification", null, Map.of("token", "")).expect(400);
    }

    @Test
    void theLinkCanBeSentAgainButNotOverAndOver() throws Exception {
        String email = uniqueEmail("reenvio");
        register(email).expect(201);
        String token = login(email, PASSWORD);
        String first = linkTokenFor(email, VERIFY);

        // Recién enviado: no se manda otro. Sin sesión, tampoco.
        Response tooSoon =
                post("/api/v1/auth/email-verification/resend", token, null).expect(409);
        assertThat(tooSoon.<String>json("$.detail")).contains("Acabamos de enviarte un enlace");
        post("/api/v1/auth/email-verification/resend", null, null).expect(401);

        // Pasado el minuto llega uno nuevo, y el anterior deja de servir.
        jdbc.update(
                "UPDATE account_tokens SET created_at = created_at - INTERVAL 2 MINUTE WHERE user_id = ?", idOf(email));
        post("/api/v1/auth/email-verification/resend", token, null).expect(204);
        awaitMailTo(email, 2);
        String second = linkTokenFor(email, VERIFY);
        assertThat(second).isNotEqualTo(first);
        post("/api/v1/auth/email-verification", null, Map.of("token", first)).expect(409);
        post("/api/v1/auth/email-verification", null, Map.of("token", second)).expect(204);

        // Ya verificada, no hay nada que reenviar.
        post("/api/v1/auth/email-verification/resend", token, null).expect(409);
    }

    @Test
    void anExpiredLinkNoLongerVerifies() {
        String email = uniqueEmail("vencido");
        register(email).expect(201);
        String link = linkTokenFor(email, VERIFY);
        jdbc.update(
                "UPDATE account_tokens SET expires_at = created_at - INTERVAL 1 MINUTE WHERE user_id = ?", idOf(email));

        post("/api/v1/auth/email-verification", null, Map.of("token", link)).expect(409);
    }

    @Test
    void whoeverIsOnTheRosterAsATeacherGetsTheRoleByVerifyingTheEmail() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String teacher = uniqueEmail("listado.verifica");
        postFile(
                        "/api/v1/admin/roster",
                        macondolab,
                        "file",
                        "listado.csv",
                        MediaType.parseMediaType("text/csv"),
                        ("correo;rol;nombres;apellidos\n" + teacher + ";profesor;Carlos;Mendoza\n")
                                .getBytes(StandardCharsets.UTF_8))
                .expect(200);

        Response created = register(teacher).expect(201);
        assertThat(created.<String>json("$.role")).isEqualTo("STUDENT");
        assertThat(created.<String>json("$.pendingRole")).isEqualTo("TEACHER");

        // Abrir el enlace demuestra que el correo es suyo: el rol ya no espera a la gestión.
        verifyEmail(teacher);
        Response me = get("/api/v1/users/me", login(teacher, PASSWORD)).expect(200);
        assertThat(me.<String>json("$.role")).isEqualTo("TEACHER");
        assertThat(me.<String>json("$.pendingRole")).isNull();
    }

    @Test
    void anAccountCreatedByManagementHasNothingToVerify() {
        String email = uniqueEmail("creada");
        Response created = post(
                        "/api/v1/admin/users",
                        loginAs(Role.MACONDOLAB),
                        Map.of(
                                "firstName",
                                "Marta",
                                "lastName",
                                "Ríos",
                                "email",
                                email,
                                "password",
                                "Temporal#2026",
                                "role",
                                "JUDGE"))
                .expect(201);

        // Ya recibe su contraseña temporal en ese correo: no hace falta otro enlace.
        assertThat(created.<List<String>>json("$.pendingSteps")).doesNotContain("VERIFY_EMAIL");
    }

    // ── Recuperación de la contraseña ───────────────────────────────────────

    @Test
    void aForgottenPasswordIsReplacedWithTheLinkSentToTheEmail() throws Exception {
        String email = createAccount(Role.STUDENT);
        String session = login(email, PASSWORD);
        String nueva = "Recuperada#2026";

        post("/api/v1/auth/password-recovery", null, Map.of("email", email)).expect(202);
        MimeMessage mail = awaitMail(email, RECOVER);
        assertThat(body(mail)).contains("http://localhost:5173/expoideas/restablecer-contrasena#token=");
        String link = linkTokenFor(email, RECOVER);

        // La contraseña nueva cumple las mismas reglas que cualquiera, y se confirma.
        post(
                        "/api/v1/auth/password-reset",
                        null,
                        Map.of("token", link, "newPassword", "corta", "confirmPassword", "corta"))
                .expect(400);
        post(
                        "/api/v1/auth/password-reset",
                        null,
                        Map.of("token", link, "newPassword", nueva, "confirmPassword", "Otra#2026"))
                .expect(400);
        post("/api/v1/auth/password-reset", null, Map.of("token", link, "newPassword", nueva, "confirmPassword", nueva))
                .expect(204);

        // Entra con la nueva, la anterior ya no vale y lo que estaba abierto se cerró.
        login(email, nueva);
        post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .expect(401);
        get("/api/v1/users/me", session).expect(401);

        // El enlace sirve una sola vez.
        post(
                        "/api/v1/auth/password-reset",
                        null,
                        Map.of("token", link, "newPassword", "Tercera#2026", "confirmPassword", "Tercera#2026"))
                .expect(409);
    }

    @Test
    void askingForAnEmailWithoutAccountAnswersTheSameAndSendsNothing() throws Exception {
        String nobody = uniqueEmail("nadie.recupera");

        post("/api/v1/auth/password-recovery", null, Map.of("email", nobody)).expect(202);
        post("/api/v1/auth/password-recovery", null, Map.of("email", "no-es-un-correo"))
                .expect(400);

        // Se da tiempo a que saliera un correo, si lo hubiera.
        Thread.sleep(500);
        boolean reached = false;
        for (MimeMessage message : MAIL.getReceivedMessages()) {
            reached |= message.getAllRecipients()[0].toString().equalsIgnoreCase(nobody);
        }
        assertThat(reached).isFalse();
    }

    @Test
    void aSuspendedAccountDoesNotGetARecoveryLink() throws Exception {
        String email = createAccount(Role.STUDENT);
        post("/api/v1/admin/users/" + idOf(email) + "/suspension", loginAs(Role.MACONDOLAB), null)
                .expect(200);

        post("/api/v1/auth/password-recovery", null, Map.of("email", email)).expect(202);

        Thread.sleep(500);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM account_tokens WHERE user_id = ?", Integer.class, idOf(email)))
                .isZero();
    }

    @Test
    void recoveringThePasswordAlsoLiftsTheLockOfFailedAttempts() {
        String email = createAccount(Role.STUDENT);
        for (int attempt = 0; attempt < 5; attempt++) {
            post("/api/v1/auth/login", null, Map.of("email", email, "password", "Incorrecta#" + attempt))
                    .expect(401);
        }
        post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .expect(429);

        post("/api/v1/auth/password-recovery", null, Map.of("email", email)).expect(202);
        post(
                        "/api/v1/auth/password-reset",
                        null,
                        Map.of(
                                "token",
                                linkTokenFor(email, RECOVER),
                                "newPassword",
                                "Recuperada#2026",
                                "confirmPassword",
                                "Recuperada#2026"))
                .expect(204);

        login(email, "Recuperada#2026");
    }

    @Test
    void theTokensAreStoredHashed() {
        String email = uniqueEmail("hash");
        register(email).expect(201);
        String link = linkTokenFor(email, VERIFY);

        String stored = jdbc.queryForObject(
                "SELECT token_hash FROM account_tokens WHERE user_id = ?", String.class, idOf(email));

        // Quien lea la tabla no tiene con qué verificar una cuenta ajena.
        assertThat(stored).hasSize(64).isNotEqualTo(link).doesNotContain(link);
    }
}
