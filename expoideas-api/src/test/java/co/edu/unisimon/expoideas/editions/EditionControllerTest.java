package co.edu.unisimon.expoideas.editions;

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

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Ediciones: lectura pública, escritura de MacondoLab y contrato HTTP. */
@SecuredWebMvcTest(EditionController.class)
class EditionControllerTest {

    private static final String BODY = """
            {
              "name": "Expoideas 2026-2",
              "registrationOpensOn": "2026-11-03",
              "registrationClosesOn": "2026-11-14",
              "submissionClosesOn": "2026-11-28",
              "tracks": [
                { "track": "INNPRENDE_I", "minMembers": 2, "maxMembers": 5 },
                { "track": "INNPRENDE_II", "minMembers": 2, "maxMembers": 5 }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EditionService editionService;

    @Test
    void theListIsPublic() throws Exception {
        when(editionService.list()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/editions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Expoideas 2026-2"))
                .andExpect(jsonPath("$[0].registrationOpen").value(true))
                .andExpect(jsonPath("$[0].tracks[0].track").value("INNPRENDE_I"))
                .andExpect(jsonPath("$[0].tracks[0].maxMembers").value(5));
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void macondoLabCreatesAndEditsEditions() throws Exception {
        when(editionService.create(any())).thenReturn(response());
        when(editionService.update(eq(1), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/editions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(put("/api/v1/editions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "coordinacion@unisimon.edu.co", roles = "MACONDOLAB")
    void macondoLabPublishesAndHidesGrades() throws Exception {
        when(editionService.publishGrades(1, Track.INNPRENDE_I, "coordinacion@unisimon.edu.co"))
                .thenReturn(response());
        when(editionService.hideGrades(1, Track.INNPRENDE_I)).thenReturn(response());

        mockMvc.perform(put("/api/v1/editions/1/tracks/INNPRENDE_I/grades-publication"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/editions/1/tracks/INNPRENDE_I/grades-publication"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/editions/1/tracks/OTRA/grades-publication"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void otherRolesDoNotWriteEditions() throws Exception {
        mockMvc.perform(put("/api/v1/editions/1/tracks/INNPRENDE_I/grades-publication"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/editions/1/tracks/INNPRENDE_I/grades-publication"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/editions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(editionService);
    }

    @Test
    void writingWithoutSessionIs401() throws Exception {
        mockMvc.perform(post("/api/v1/editions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(editionService);
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void missingDatesAre400() throws Exception {
        mockMvc.perform(post("/api/v1/editions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Expoideas\",\"tracks\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.registrationOpensOn").exists())
                .andExpect(jsonPath("$.fields.tracks").value("Configura las dos cátedras"));

        verifyNoInteractions(editionService);
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void anUnknownTrackIs400() throws Exception {
        mockMvc.perform(post("/api/v1/editions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("INNPRENDE_II", "INNPRENDE_III")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(editionService);
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void crossedDatesComeBackOnTheirField() throws Exception {
        when(editionService.create(any()))
                .thenThrow(new InvalidFieldsException(
                        "registrationClosesOn", "Las inscripciones no pueden cerrar antes de abrir"));

        mockMvc.perform(post("/api/v1/editions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.registrationClosesOn")
                        .value("Las inscripciones no pueden cerrar antes de abrir"));
    }

    private static EditionResponse response() {
        return new EditionResponse(
                1,
                "Expoideas 2026-2",
                LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 14),
                LocalDate.of(2026, 11, 28),
                true,
                true,
                List.of(
                        new TrackSettingsResponse(Track.INNPRENDE_I, 2, 5, 1, null),
                        new TrackSettingsResponse(Track.INNPRENDE_II, 2, 5, 2, null)));
    }
}
