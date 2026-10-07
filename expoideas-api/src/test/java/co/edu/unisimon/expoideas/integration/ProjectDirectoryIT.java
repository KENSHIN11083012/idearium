package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * Listado de proyectos para la gestión y los docentes: quién ve qué, los
 * filtros y la exportación a CSV.
 */
class ProjectDirectoryIT extends IntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void theListingDoesNotQueryTheDatabaseOnceMorePerProject() {
        String batch = "Lote-" + System.nanoTime();
        String macondolab = loginAs(Role.MACONDOLAB);
        String jurorEmail = createAccount(Role.JUDGE);
        String juror = login(jurorEmail, PASSWORD);
        Response rubric = get("/api/v1/rubrics/INNPRENDE_I", juror).expect(200);
        List<Map<String, Object>> scores = new ArrayList<>();
        for (int criterion = 0; criterion < 6; criterion++) {
            scores.add(Map.of(
                    "criterionId", rubric.<Integer>json("$.criteria[" + criterion + "].id"),
                    "levelId", rubric.<Integer>json("$.criteria[" + criterion + "].levels[4].id")));
        }
        // Seis proyectos calificados: dos del grupo «a» y cuatro del «b».
        for (String name : List.of("-a1", "-a2", "-b1", "-b2", "-b3", "-b4")) {
            newProject(batch + name);
            int id = jdbc.queryForObject("SELECT id FROM projects WHERE title = ?", Integer.class, batch + name);
            post("/api/v1/projects/" + id + "/jurors", macondolab, Map.of("email", jurorEmail))
                    .expect(201);
            put("/api/v1/projects/" + id + "/evaluations/mine", juror, Map.of("scores", scores))
                    .expect(200);
        }

        long forTwo = statementsOf("/api/v1/projects?search=" + batch + "-a", macondolab, 2);
        long forSix = statementsOf("/api/v1/projects?search=" + batch, macondolab, 6);

        // Con el triple de proyectos, las mismas consultas: la nota de cada uno no cuesta una más.
        assertThat(forSix).isEqualTo(forTwo);
        assertThat(forSix).isLessThanOrEqualTo(14);
    }

    @Test
    void theListingComesInPagesNewestFirst() {
        String batch = "Pagina-" + System.nanoTime();
        String macondolab = loginAs(Role.MACONDOLAB);
        for (String name : List.of("-1", "-2", "-3")) {
            newProject(batch + name);
        }
        String listing = "/api/v1/projects?search=" + batch;

        Response first = get(listing + "&size=2", macondolab).expect(200);
        assertThat(first.<List<String>>json("$.items[*].title")).containsExactly(batch + "-3", batch + "-2");
        assertThat(first.<Integer>json("$.page")).isZero();
        assertThat(first.<Integer>json("$.size")).isEqualTo(2);
        assertThat(first.<Integer>json("$.totalItems")).isEqualTo(3);
        assertThat(first.<Integer>json("$.totalPages")).isEqualTo(2);

        Response second = get(listing + "&size=2&page=1", macondolab).expect(200);
        assertThat(second.<List<String>>json("$.items[*].title")).containsExactly(batch + "-1");
        // Cada proyecto llega con su equipo aunque la consulta vaya por páginas.
        assertThat(second.<Integer>json("$.items[0].members")).isEqualTo(1);

        // Más allá de la última página no hay nada, y el tamaño tiene tope.
        assertThat(get(listing + "&size=2&page=7", macondolab).expect(200).<List<Object>>json("$.items"))
                .isEmpty();
        assertThat(get(listing + "&size=5000", macondolab).expect(200).<Integer>json("$.size"))
                .isEqualTo(200);

        // El CSV no va por páginas: trae todo lo que cumple los filtros.
        String csv = get("/api/v1/projects/export?search=" + batch, macondolab)
                .expect(200)
                .body();
        assertThat(csv).contains(batch + "-1", batch + "-2", batch + "-3");
    }

    @Test
    void theListingAndTheCsvComeInTheOrderTheViewerChooses() {
        String batch = "Orden-" + System.nanoTime();
        String macondolab = loginAs(Role.MACONDOLAB);
        // Inscritos en un orden que no es el alfabético.
        for (String name : List.of(" Mango", " Arepa", " Zapote")) {
            newProject(batch + name);
        }
        String listing = "/api/v1/projects?search=" + batch;

        // Sin pedir nada, como siempre: el más reciente primero.
        assertThat(get(listing, macondolab).expect(200).<List<String>>json("$.items[*].title"))
                .containsExactly(batch + " Zapote", batch + " Arepa", batch + " Mango");
        assertThat(get(listing + "&sort=RECENT", macondolab).expect(200).<List<String>>json("$.items[*].title"))
                .containsExactly(batch + " Zapote", batch + " Arepa", batch + " Mango");

        // Por título, y el orden se mantiene de una página a la siguiente.
        assertThat(get(listing + "&sort=TITLE&size=2", macondolab).expect(200).<List<String>>json("$.items[*].title"))
                .containsExactly(batch + " Arepa", batch + " Mango");
        assertThat(get(listing + "&sort=TITLE&size=2&page=1", macondolab)
                        .expect(200)
                        .<List<String>>json("$.items[*].title"))
                .containsExactly(batch + " Zapote");

        // El CSV sale igual que se ve en pantalla.
        String csv = get("/api/v1/projects/export?sort=TITLE&search=" + batch, macondolab)
                .expect(200)
                .body();
        assertThat(csv.indexOf(batch + " Arepa")).isPositive().isLessThan(csv.indexOf(batch + " Mango"));
        assertThat(csv.indexOf(batch + " Mango")).isLessThan(csv.indexOf(batch + " Zapote"));

        // Un orden que no existe es un error de quien pide, no un 500.
        get(listing + "&sort=CUALQUIERA", macondolab).expect(400);
    }

    /** Cuántas sentencias le cuesta a la base responder ese listado, que debe traer esos proyectos. */
    private long statementsOf(String uri, String token, int projects) {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        Response listing = get(uri, token).expect(200);
        long statements = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);

        assertThat(listing.<List<Double>>json("$.items[*].grade"))
                .hasSize(projects)
                .containsOnly(5.0);
        return statements;
    }

    @Test
    void managementSeesEveryProjectAndFiltersIt() {
        Fixture first = newProject("Proyecto de gestión A " + System.nanoTime());
        Fixture second = newProject("Proyecto de gestión B " + System.nanoTime());
        String macondolab = loginAs(Role.MACONDOLAB);

        List<String> all = get("/api/v1/projects", macondolab).expect(200).json("$.items[*].title");
        assertThat(all).contains(first.title(), second.title());

        List<String> byTeacher = get("/api/v1/projects?teacherId=" + idOf(first.teacherEmail()), macondolab)
                .expect(200)
                .json("$.items[*].title");
        assertThat(byTeacher).containsExactly(first.title());

        List<String> bySearch = get("/api/v1/projects?search=" + second.title().substring(0, 18), macondolab)
                .expect(200)
                .json("$.items[*].title");
        assertThat(bySearch).contains(second.title());

        List<String> byOtherTrack = get("/api/v1/projects?track=INNPRENDE_II", macondolab)
                .expect(200)
                .json("$.items[*].title");
        assertThat(byOtherTrack).doesNotContain(first.title());
    }

    @Test
    void aTeacherOnlySeesTheProjectsThatNameThem() {
        Fixture mine = newProject("Proyecto del docente " + System.nanoTime());
        Fixture other = newProject("Proyecto de otro docente " + System.nanoTime());

        String teacher = login(mine.teacherEmail(), PASSWORD);
        List<String> titles = get("/api/v1/projects", teacher).expect(200).json("$.items[*].title");
        assertThat(titles).containsExactly(mine.title());

        // Pedir los de otro docente no cambia nada: sigue viendo los suyos.
        List<String> forced = get("/api/v1/projects?teacherId=" + idOf(other.teacherEmail()), teacher)
                .expect(200)
                .json("$.items[*].title");
        assertThat(forced).containsExactly(mine.title());
    }

    @Test
    void studentsAndVisitorsDoNotGetTheDirectory() {
        get("/api/v1/projects", loginAs(Role.STUDENT)).expect(403);
        get("/api/v1/projects", loginAs(Role.JUDGE)).expect(403);
        get("/api/v1/projects", null).expect(401);
    }

    @Test
    void theSummaryCountsTheTeamAndTheDeliverables() {
        Fixture project = newProject("Proyecto con conteo " + System.nanoTime());
        String macondolab = loginAs(Role.MACONDOLAB);

        Response summary = get("/api/v1/projects?search=" + project.title().substring(0, 18), macondolab)
                .expect(200);
        List<Integer> members = summary.json("$.items[?(@.title == '" + project.title() + "')].members");
        List<String> leaders = summary.json("$.items[?(@.title == '" + project.title() + "')].leader");
        assertThat(members).containsExactly(1);
        assertThat(leaders).containsExactly("Prueba STUDENT");
    }

    @Test
    void theListIsExportedAsCsvForASpreadsheet() {
        Fixture project = newProject("Proyecto exportado " + System.nanoTime());
        String macondolab = loginAs(Role.MACONDOLAB);

        Response csv = get("/api/v1/projects/export", macondolab).expect(200);
        String body = new String(csv.bytes(), StandardCharsets.UTF_8);

        assertThat(csv.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("text/csv");
        assertThat(csv.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        // Con BOM, para que Excel en español muestre bien las tildes.
        assertThat(body).startsWith("﻿Edición;Cátedra;Proyecto");
        assertThat(body).contains(project.title()).contains("INNPRENDE I · Despegue");

        get("/api/v1/projects/export", loginAs(Role.STUDENT)).expect(403);
    }

    // ── Datos de apoyo ──────────────────────────────────────────────────────

    private record Fixture(String title, String leaderToken, String teacherEmail) {}

    private Fixture newProject(String title) {
        String leader = createAccount(Role.STUDENT);
        String teacher = createAccount(Role.TEACHER);
        String token = login(leader, PASSWORD);

        Map<String, Object> body = new HashMap<>();
        body.put("editionId", openEdition());
        body.put("track", "INNPRENDE_I");
        body.put("title", title);
        body.put("summary", "Propuesta de valor de prueba.");
        body.put(
                "sectorId",
                ((List<Integer>) get("/api/v1/sectors", null).expect(200).json("$[*].id")).getFirst());
        body.put("teacherId", idOf(teacher));
        body.put("semester", 5);
        body.put("phone", "3001234567");
        post("/api/v1/projects", token, body).expect(201);

        return new Fixture(title, token, teacher);
    }
}
