package co.edu.unisimon.expoideas.presentations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Sustentaciones: la gestión programa; el equipo consulta; todo pide sesión. */
@SecuredWebMvcTest(PresentationController.class)
class PresentationControllerTest {

    private static final String BODY = """
            {"startsAt":"2026-11-20T09:30","place":"Auditorio Jorge Artel","notes":"Llegar 15 minutos antes."}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PresentationService presentationService;

    @Test
    @WithMockUser(username = "ana@unisimon.edu.co", roles = "STUDENT")
    void theTeamSeesItsPresentationOr204WhenThereIsNone() throws Exception {
        when(presentationService.get(10, "ana@unisimon.edu.co")).thenReturn(Optional.of(response()));
        when(presentationService.get(11, "ana@unisimon.edu.co")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/10/presentation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place").value("Auditorio Jorge Artel"))
                .andExpect(jsonPath("$.startsAt").value("2026-11-20T09:30:00"));
        mockMvc.perform(get("/api/v1/projects/11/presentation")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void managementSchedulesChangesAndCancels() throws Exception {
        when(presentationService.schedule(eq(10), any())).thenReturn(response());
        when(presentationService.agenda(1, Track.INNPRENDE_I))
                .thenReturn(List.of(new PresentationAgendaResponse(
                        3,
                        10,
                        "BioSensor",
                        "Ana Pérez",
                        "Carlos Mendoza",
                        LocalDateTime.of(2026, 11, 20, 9, 30),
                        "Auditorio Jorge Artel",
                        null)));

        mockMvc.perform(put("/api/v1/projects/10/presentation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(10));
        mockMvc.perform(put("/api/v1/projects/10/presentation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"place\":\"Auditorio\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.startsAt").exists());
        mockMvc.perform(delete("/api/v1/projects/10/presentation")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/presentations").param("editionId", "1").param("track", "INNPRENDE_I"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectTitle").value("BioSensor"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teachersAndStudentsOnlyRead() throws Exception {
        mockMvc.perform(put("/api/v1/projects/10/presentation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/projects/10/presentation")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/presentations").param("editionId", "1").param("track", "INNPRENDE_I"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(presentationService);
    }

    @Test
    void withoutASessionThereIsNoAgenda() throws Exception {
        mockMvc.perform(get("/api/v1/projects/10/presentation")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/presentations").param("editionId", "1").param("track", "INNPRENDE_I"))
                .andExpect(status().isUnauthorized());
    }

    private static PresentationResponse response() {
        return new PresentationResponse(
                3,
                10,
                LocalDateTime.of(2026, 11, 20, 9, 30),
                "Auditorio Jorge Artel",
                "Llegar 15 minutos antes.",
                LocalDateTime.of(2026, 11, 1, 8, 0));
    }
}
