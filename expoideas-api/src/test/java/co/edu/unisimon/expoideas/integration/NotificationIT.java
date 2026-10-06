package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.notifications.MailFailureRecorder;
import co.edu.unisimon.expoideas.users.Role;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Los correos de la plataforma contra un SMTP de verdad (GreenMail): invitación a
 * un equipo, cuenta creada por la gestión y sustentación programada o cambiada.
 * Salen después del commit y en otro hilo, así que se espera a que lleguen.
 * También, con qué límites se envían y dónde queda el que no se pudo enviar.
 */
class NotificationIT extends IntegrationTest {

    private static final String TRACK = "INNPRENDE_I";

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private ThreadPoolTaskExecutor mailExecutor;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MailFailureRecorder failures;

    @Test
    void mailIsSentWithLimitsSoAServerThatDoesNotAnswerCannotHangThePlatform() {
        // Pocos hilos y una cola con tope; si se llena, envía quien lo pidió en vez de rechazarse.
        assertThat(mailExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(mailExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(mailExecutor.getQueueCapacity()).isEqualTo(500);
        assertThat(mailExecutor.getThreadNamePrefix()).isEqualTo("correo-");
        assertThat(mailExecutor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);

        // Conectar, leer y escribir tienen tiempo de espera: JavaMail no lo pone solo.
        assertThat(((JavaMailSenderImpl) mailSender).getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "10000")
                .containsEntry("mail.smtp.timeout", "15000")
                .containsEntry("mail.smtp.writetimeout", "15000");
    }

    @Test
    void aMailThatCouldNotBeSentIsLeftInTheAuditTrailWithoutItsBody() {
        String lost = uniqueEmail("correo.perdido");

        // Como lo deja NotificationListener tras el último intento: desde el hilo del correo, sin sesión.
        failures.record(
                lost,
                "Tu cuenta en Idearium",
                3,
                new MailSendException(
                        "Mail server connection failed", new java.net.ConnectException("Connection refused")));

        List<Map<String, Object>> rows = get("/api/v1/admin/audit?action=MAIL_FAILED&size=100", loginAs(Role.ADMIN))
                .expect(200)
                .json("$.items");
        assertThat(rows)
                .filteredOn(row -> lost.equals(row.get("targetLabel")))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("actionLabel", "Correo que no se pudo enviar")
                        .containsEntry("targetType", "MAIL")
                        .containsEntry("targetId", null)
                        .containsEntry("actorEmail", null)
                        .containsEntry(
                                "detail",
                                "\"Tu cuenta en Idearium\" · intentos: 3 · ConnectException: Connection refused"));
    }

    @Test
    void anInvitationReachesTheInvitedStudent() throws Exception {
        String leader = createAccount(Role.STUDENT);
        String member = createAccount(Role.STUDENT);
        String teacher = createAccount(Role.TEACHER);
        String leaderToken = login(leader, PASSWORD);
        int projectId = createProject(leaderToken, teacher, "Riego inteligente " + System.nanoTime());

        post("/api/v1/projects/" + projectId + "/invitations", leaderToken, Map.of("email", member))
                .expect(201);

        MimeMessage mail = awaitMailTo(member, 1).getFirst();
        assertThat(mail.getSubject()).contains("Te invitaron al proyecto").contains("Riego inteligente");
        assertThat(body(mail))
                .contains("Prueba STUDENT")
                .contains("INNPRENDE I · Despegue")
                .contains("Entra a Idearium")
                .contains("Mis proyectos");
        assertThat(mail.getFrom()[0].toString()).isEqualTo("expoideas@pruebas.local");
    }

    @Test
    void anAccountCreatedByManagementGetsItsTemporaryPassword() throws Exception {
        String email = uniqueEmail("jurado.externo");
        post(
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

        MimeMessage mail = awaitMailTo(email, 1).getFirst();
        assertThat(mail.getSubject()).isEqualTo("Tu cuenta en Idearium");
        assertThat(body(mail))
                .contains("Marta Ríos")
                .contains("Jurado")
                .contains("Temporal#2026")
                .contains(email);
    }

    @Test
    void schedulingAPresentationTellsTheTeamAndTheTeacherAndSoDoesChangingIt() throws Exception {
        String leader = createAccount(Role.STUDENT);
        String member = createAccount(Role.STUDENT);
        String teacher = createAccount(Role.TEACHER);
        String leaderToken = login(leader, PASSWORD);
        String memberToken = login(member, PASSWORD);
        int projectId = createProject(leaderToken, teacher, "BioSensor " + System.nanoTime());
        post("/api/v1/projects/" + projectId + "/invitations", leaderToken, Map.of("email", member))
                .expect(201);
        int invitationId = get("/api/v1/invitations", memberToken).expect(200).json("$[0].id");
        post("/api/v1/invitations/" + invitationId + "/acceptance", memberToken, null)
                .expect(200);
        String macondolab = loginAs(Role.MACONDOLAB);

        // Antes de programarla no hay cita, y programar es cosa de la gestión.
        get("/api/v1/projects/" + projectId + "/presentation", leaderToken).expect(204);
        Map<String, Object> cita = new HashMap<>();
        cita.put("startsAt", "2026-11-20T09:30");
        cita.put("place", "Auditorio Jorge Artel");
        cita.put("notes", "Llegar 15 minutos antes.");
        put("/api/v1/projects/" + projectId + "/presentation", leaderToken, cita)
                .expect(403);
        put("/api/v1/projects/" + projectId + "/presentation", login(teacher, PASSWORD), cita)
                .expect(403);

        Response scheduled = put("/api/v1/projects/" + projectId + "/presentation", macondolab, cita)
                .expect(200);
        assertThat(scheduled.<String>json("$.startsAt")).isEqualTo("2026-11-20T09:30:00");

        for (String recipient : List.of(leader, member, teacher)) {
            MimeMessage mail = awaitMail(recipient, "Sustentación programada");
            assertThat(body(mail))
                    .contains("Viernes 20 de noviembre de 2026")
                    .contains("9:30 a. m.")
                    .contains("Auditorio Jorge Artel")
                    .contains("Llegar 15 minutos antes.");
        }

        // El equipo y el profesor la ven; alguien de fuera, no. La gestión la ve en su agenda.
        Response mine = get("/api/v1/projects/" + projectId + "/presentation", memberToken)
                .expect(200);
        assertThat(mine.<String>json("$.place")).isEqualTo("Auditorio Jorge Artel");
        get("/api/v1/projects/" + projectId + "/presentation", login(teacher, PASSWORD))
                .expect(200);
        get("/api/v1/projects/" + projectId + "/presentation", loginAs(Role.STUDENT))
                .expect(404);
        List<Integer> agenda = get("/api/v1/presentations?editionId=" + openEdition() + "&track=" + TRACK, macondolab)
                .expect(200)
                .json("$[*].projectId");
        assertThat(agenda).contains(projectId);

        // Cambiarla vuelve a avisar, ahora como cambio.
        cita.put("place", "Sala 302");
        put("/api/v1/projects/" + projectId + "/presentation", macondolab, cita).expect(200);
        MimeMessage changed = awaitMail(leader, "Cambió la sustentación");
        assertThat(body(changed)).contains("Sala 302");

        delete("/api/v1/projects/" + projectId + "/presentation", macondolab).expect(204);
        get("/api/v1/projects/" + projectId + "/presentation", leaderToken).expect(204);
    }

    private int createProject(String leaderToken, String teacherEmail, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", TRACK);
        body.put("title", title);
        body.put("summary", "Una propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(teacherEmail));
        return post("/api/v1/projects", leaderToken, body).expect(201).json("$.id");
    }
}
