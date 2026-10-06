package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Ediciones de la Expo contra MySQL: las escribe MacondoLab, cualquiera las lee
 * y la BD impide que dos tengan el mismo nombre o que una cátedra se repita.
 */
class EditionIT extends IntegrationTest {

    @Test
    void macondoLabCreatesAnEditionAndAnyoneCanReadIt() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String name = "Expoideas IT " + System.nanoTime();
        LocalDate opens = LocalDate.of(2030, 3, 1);

        int id = post("/api/v1/editions", macondolab, edition(name, opens))
                .expect(201)
                .json("$.id");

        Response detail = get("/api/v1/editions/" + id, null).expect(200);
        List<String> tracks = detail.json("$.tracks[*].track");
        assertThat(tracks).containsExactlyInAnyOrder("INNPRENDE_I", "INNPRENDE_II");
        assertThat(detail.json("$.name").toString()).isEqualTo(name);
        // Una edición de 2030 todavía no abre.
        assertThat((Boolean) detail.json("$.registrationOpen")).isFalse();

        List<String> names = get("/api/v1/editions", null).expect(200).json("$[*].name");
        assertThat(names).contains(name);
    }

    @Test
    void theSettingsOfEachTrackCanBeChanged() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String name = "Expoideas IT " + System.nanoTime();
        LocalDate opens = LocalDate.of(2031, 3, 1);

        int id = post("/api/v1/editions", macondolab, edition(name, opens))
                .expect(201)
                .json("$.id");

        Map<String, Object> changed = edition(name, opens);
        changed.put(
                "tracks",
                List.of(
                        Map.of("track", "INNPRENDE_I", "minMembers", 1, "maxMembers", 8),
                        Map.of("track", "INNPRENDE_II", "minMembers", 2, "maxMembers", 6)));

        Response updated = put("/api/v1/editions/" + id, macondolab, changed).expect(200);
        List<Integer> maximums = updated.json("$.tracks[*].maxMembers");
        assertThat(maximums).containsExactlyInAnyOrder(8, 6);

        // Sigue habiendo una sola fila por cátedra: la edición no acumula configuraciones.
        List<String> tracks = get("/api/v1/editions/" + id, null).expect(200).json("$.tracks[*].track");
        assertThat(tracks).hasSize(2);
    }

    @Test
    void theNameIsUniqueAndDatesCannotOverlap() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String name = "Expoideas IT " + System.nanoTime();
        LocalDate opens = LocalDate.of(2032, 3, 1);

        post("/api/v1/editions", macondolab, edition(name, opens)).expect(201);
        post("/api/v1/editions", macondolab, edition(name, opens.plusYears(5))).expect(409);

        Response overlapping = post("/api/v1/editions", macondolab, edition(name + " bis", opens.plusDays(10)))
                .expect(400);
        assertThat(overlapping.json("$.fields.registrationOpensOn").toString()).contains(name);
    }

    @Test
    void otherRolesCannotWriteEditions() {
        LocalDate opens = LocalDate.of(2033, 3, 1);

        post("/api/v1/editions", loginAs(Role.TEACHER), edition("Expoideas docente", opens))
                .expect(403);
        post("/api/v1/editions", loginAs(Role.STUDENT), edition("Expoideas estudiante", opens))
                .expect(403);
        post("/api/v1/editions", null, edition("Expoideas sin sesión", opens)).expect(401);
    }

    @Test
    void anEditionThatDoesNotExistIs404() {
        get("/api/v1/editions/999999", null).expect(404);
    }

    /** Cuerpo de una edición con las dos cátedras y plazos de un mes. */
    private static Map<String, Object> edition(String name, LocalDate opens) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("registrationOpensOn", opens.toString());
        body.put("registrationClosesOn", opens.plusWeeks(2).toString());
        body.put("submissionClosesOn", opens.plusWeeks(4).toString());
        body.put(
                "tracks",
                List.of(
                        Map.of("track", "INNPRENDE_I", "minMembers", 2, "maxMembers", 5),
                        Map.of("track", "INNPRENDE_II", "minMembers", 2, "maxMembers", 5)));
        return body;
    }

    @Test
    void anEmptySlotAmongTheTracksIsRejectedNotAnError() {
        Map<String, Object> body =
                new HashMap<>(edition("Expoideas IT " + System.nanoTime(), LocalDate.of(2031, 3, 1)));
        body.put("tracks", Arrays.asList((Object) null));

        post("/api/v1/editions", loginAs(Role.MACONDOLAB), body).expect(400);
    }
}
