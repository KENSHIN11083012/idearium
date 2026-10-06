package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.users.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Catálogos: lectura pública; los institucionales (sedes, facultades, programas)
 * los escribe el administrador y los de clasificación (sectores, keywords) también MacondoLab.
 */
class CatalogIT extends IntegrationTest {

    @Test
    void programsAreListedWithTheirFaculty() {
        String admin = loginAs(Role.ADMIN);
        int faculty = createFaculty(admin);
        int program = createProgram(admin, faculty);

        Response list = get("/api/v1/academic-programs", null).expect(200);
        List<Integer> faculties = list.json("$[?(@.id == " + program + ")].facultyId");
        List<String> names = list.json("$[?(@.id == " + program + ")].faculty");
        assertThat(faculties).containsExactly(faculty);
        assertThat(names).singleElement().asString().startsWith("Facultad ");
    }

    @Test
    void programRequiresAnExistingFaculty() {
        String admin = loginAs(Role.ADMIN);
        post("/api/v1/academic-programs", admin, Map.of("name", "Huérfano")).expect(400);
        post("/api/v1/academic-programs", admin, Map.of("name", "Huérfano", "facultyId", 999_999))
                .expect(404);
    }

    @Test
    void institutionalCatalogsAreAdminOnly() {
        String macondolab = loginAs(Role.MACONDOLAB);
        post("/api/v1/faculties", macondolab, Map.of("name", "No permitida")).expect(403);
        post("/api/v1/campuses", macondolab, Map.of("name", "No permitida")).expect(403);
        post("/api/v1/campuses", null, Map.of("name", "Sin sesión")).expect(401);
    }

    @Test
    void classificationCatalogsAreWrittenByManagement() {
        String macondolab = loginAs(Role.MACONDOLAB);
        String name = "Sector " + System.nanoTime();

        int id = post("/api/v1/sectors", macondolab, Map.of("name", name))
                .expect(201)
                .json("$.id");
        post("/api/v1/sectors", macondolab, Map.of("name", name)).expect(409);
        put("/api/v1/sectors/" + id, macondolab, Map.of("name", name + " (editado)"))
                .expect(200);
        post("/api/v1/keywords", macondolab, Map.of("name", "keyword-" + System.nanoTime()))
                .expect(201);

        post("/api/v1/sectors", loginAs(Role.STUDENT), Map.of("name", "Otro sector"))
                .expect(403);

        List<String> names = get("/api/v1/sectors", null).expect(200).json("$[*].name");
        assertThat(names).contains(name + " (editado)");
    }

    @Test
    void namesAreUniqueAndProgramNamesAreUniquePerFaculty() {
        String admin = loginAs(Role.ADMIN);
        String faculty = "Facultad única " + System.nanoTime();
        int first = post("/api/v1/faculties", admin, Map.of("name", faculty))
                .expect(201)
                .json("$.id");
        post("/api/v1/faculties", admin, Map.of("name", faculty)).expect(409);
        post("/api/v1/campuses", admin, Map.of("name", "Barranquilla")).expect(409);

        int second = createFaculty(admin);
        post("/api/v1/academic-programs", admin, Map.of("name", "Derecho", "facultyId", first))
                .expect(201);
        post("/api/v1/academic-programs", admin, Map.of("name", "Derecho", "facultyId", second))
                .expect(201);
        post("/api/v1/academic-programs", admin, Map.of("name", "Derecho", "facultyId", first))
                .expect(409);
    }

    @Test
    void updatingMissingItemIs404() {
        String admin = loginAs(Role.ADMIN);
        put("/api/v1/faculties/999999", admin, Map.of("name", "Nada")).expect(404);
    }
}
