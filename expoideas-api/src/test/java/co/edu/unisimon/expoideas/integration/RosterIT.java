package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * El listado de la cátedra contra MySQL: la gestión lo carga y, al registrarse,
 * quien está en él nace con su nombre y, si figura como profesor, con ese rol
 * por confirmar; quien no está, como estudiante.
 */
class RosterIT extends IntegrationTest {

    @Test
    void whoeverIsOnTheRosterRegistersWithTheirRoleAndTheRestAsStudents() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String teacher = uniqueEmail("listado.profesor");
        String student = uniqueEmail("listado.estudiante");
        String outsider = uniqueEmail("listado.ajeno");
        String csv = "﻿Correo;Rol;Nombres;Apellidos\n"
                + teacher.toUpperCase() + ";Profesor;Carlos;Mendoza\n"
                + student + ";estudiante;;\n"
                + "nadie@gmail.com;estudiante;Nadie;Externo\n";

        Response imported = postFile(
                        "/api/v1/admin/roster",
                        macondolab,
                        "file",
                        "listado.csv",
                        MediaType.parseMediaType("text/csv"),
                        csv.getBytes(StandardCharsets.UTF_8))
                .expect(200);
        assertThat(imported.<Integer>json("$.added")).isEqualTo(2);
        assertThat(imported.<Integer>json("$.rejected.length()")).isEqualTo(1);
        List<Boolean> registered = get("/api/v1/admin/roster", macondolab)
                .expect(200)
                .json("$[?(@.email == '" + teacher.toLowerCase() + "')].registered");
        assertThat(registered).containsExactly(false);

        // Nadie ha comprobado que el correo sea de quien se registra: el profesor del listado
        // nace con su nombre pero como estudiante, y el rol queda por confirmar.
        register(teacher).expect(201);
        String teacherToken = login(teacher, PASSWORD);
        Response me = get("/api/v1/users/me", teacherToken).expect(200);
        assertThat(me.<String>json("$.role")).isEqualTo("STUDENT");
        assertThat(me.<String>json("$.pendingRole")).isEqualTo("TEACHER");
        assertThat(me.<String>json("$.firstName")).isEqualTo("Carlos");
        assertThat(me.<List<String>>json("$.pendingSteps")).contains("COMPLETE_PROFILE");
        List<Integer> listedAsTeacher =
                get("/api/v1/teachers", macondolab).expect(200).json("$[?(@.id == " + idOf(teacher) + ")].id");
        assertThat(listedAsTeacher).isEmpty();

        // La gestión lo confirma desde Usuarios y la cuenta pasa a ser de profesor sin volver a entrar.
        Response confirmed = put("/api/v1/admin/users/" + idOf(teacher), macondolab, Map.of("role", "TEACHER"))
                .expect(200);
        assertThat(confirmed.<String>json("$.role")).isEqualTo("TEACHER");
        assertThat(confirmed.<String>json("$.pendingRole")).isNull();
        assertThat(get("/api/v1/users/me", teacherToken).expect(200).<String>json("$.role"))
                .isEqualTo("TEACHER");
        register(student).expect(201);
        assertThat(get("/api/v1/users/me", login(student, PASSWORD)).expect(200).<String>json("$.role"))
                .isEqualTo("STUDENT");
        register(outsider).expect(201);
        assertThat(get("/api/v1/users/me", login(outsider, PASSWORD))
                        .expect(200)
                        .<String>json("$.role"))
                .isEqualTo("STUDENT");

        // Ahora el listado dice quién ya se registró, y una fila se puede quitar.
        Response list = get("/api/v1/admin/roster", macondolab).expect(200);
        List<Boolean> after = list.json("$[?(@.email == '" + teacher.toLowerCase() + "')].registered");
        assertThat(after).containsExactly(true);
        int studentRow = ((List<Integer>) list.json("$[?(@.email == '" + student + "')].id")).getFirst();
        delete("/api/v1/admin/roster/" + studentRow, macondolab).expect(204);
        delete("/api/v1/admin/roster/" + studentRow, macondolab).expect(404);

        // El listado es de la gestión.
        get("/api/v1/admin/roster", login(teacher, PASSWORD)).expect(403);
        postFile(
                        "/api/v1/admin/roster",
                        macondolab,
                        "file",
                        "vacio.csv",
                        MediaType.parseMediaType("text/csv"),
                        new byte[0])
                .expect(400);
        // Una petición que no trae archivo (ni siquiera es multipart) se rechaza, no rompe.
        post("/api/v1/admin/roster", macondolab, Map.of("file", "listado.csv")).expect(400);
    }
}
