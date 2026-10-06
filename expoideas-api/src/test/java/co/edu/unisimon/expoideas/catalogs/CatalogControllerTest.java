package co.edu.unisimon.expoideas.catalogs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Catálogos: lectura pública, escritura por rol y contrato HTTP. */
@SecuredWebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @Test
    void campusListIsPublic() throws Exception {
        when(catalogService.listCampuses()).thenReturn(List.of(new CatalogItemResponse(1, "Barranquilla")));

        mockMvc.perform(get("/api/v1/campuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Barranquilla"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreatesAndEditsCampuses() throws Exception {
        when(catalogService.createCampus(new CatalogItemRequest("Cúcuta")))
                .thenReturn(new CatalogItemResponse(2, "Cúcuta"));
        when(catalogService.updateCampus(eq(2), any())).thenReturn(new CatalogItemResponse(2, "Cúcuta Centro"));

        mockMvc.perform(post("/api/v1/campuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cúcuta\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
        mockMvc.perform(put("/api/v1/campuses/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cúcuta Centro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cúcuta Centro"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blankNameIs400() throws Exception {
        mockMvc.perform(post("/api/v1/sectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("El nombre es obligatorio"));

        verifyNoInteractions(catalogService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void repeatedNameIs409() throws Exception {
        when(catalogService.createKeyword(any()))
                // Como llega de MySQL: con el código de «entrada duplicada».
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new java.sql.SQLIntegrityConstraintViolationException(
                                "Duplicate entry 'fintech' for key 'keywords.uk_keywords_name'", "23000", 1062)));

        mockMvc.perform(post("/api/v1/keywords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"fintech\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un registro con esos datos"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingItemIs404() throws Exception {
        when(catalogService.updateFaculty(eq(99), any()))
                .thenThrow(new NoSuchElementException("No existe una facultad con ID: 99"));

        mockMvc.perform(put("/api/v1/faculties/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ingeniería\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe una facultad con ID: 99"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void catalogsCannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/api/v1/campuses/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.detail").value("Método HTTP no soportado para este recurso"));
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void macondoLabWritesClassificationButNotStructure() throws Exception {
        when(catalogService.createSector(new CatalogItemRequest("Gastronomía")))
                .thenReturn(new CatalogItemResponse(4, "Gastronomía"));

        mockMvc.perform(post("/api/v1/sectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gastronomía\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/campuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cúcuta\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/academic-programs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Derecho\",\"facultyId\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentsDoNotWriteCatalogs() throws Exception {
        mockMvc.perform(post("/api/v1/campuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cúcuta\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/sectors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fintech\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(catalogService);
    }

    @Test
    void writingWithoutSessionIs401() throws Exception {
        mockMvc.perform(post("/api/v1/campuses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cúcuta\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(catalogService);
    }

    // ── Programas académicos ────────────────────────────────────────────────

    @Test
    void programListIncludesTheFaculty() throws Exception {
        when(catalogService.listAcademicPrograms())
                .thenReturn(List.of(new AcademicProgramResponse(1, "Ingeniería de Sistemas", 2, "Ingeniería")));

        mockMvc.perform(get("/api/v1/academic-programs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].facultyId").value(2))
                .andExpect(jsonPath("$[0].faculty").value("Ingeniería"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreatesAProgramWithItsFaculty() throws Exception {
        when(catalogService.createAcademicProgram(new AcademicProgramRequest("Derecho", 3)))
                .thenReturn(new AcademicProgramResponse(9, "Derecho", 3, "Ciencias Jurídicas"));

        mockMvc.perform(post("/api/v1/academic-programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Derecho\",\"facultyId\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facultyId").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void programWithoutFacultyIs400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Derecho\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.facultyId").exists());

        verifyNoInteractions(catalogService);
    }
}
