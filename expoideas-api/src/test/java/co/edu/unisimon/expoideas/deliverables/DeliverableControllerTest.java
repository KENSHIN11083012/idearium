package co.edu.unisimon.expoideas.deliverables;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import co.edu.unisimon.expoideas.support.TestData;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Entregables: MacondoLab los configura, el equipo los sube y todo pide sesión. */
@SecuredWebMvcTest({DeliverableController.class, DeliverableTypeController.class})
class DeliverableControllerTest {

    private static final String TYPE_BODY = """
            {
              "editionId": 1,
              "track": "INNPRENDE_I",
              "name": "Póster de investigación",
              "description": "Formato oficial, en PDF.",
              "kind": "DOCUMENT",
              "required": true,
              "maxFiles": 1,
              "sortOrder": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliverableService deliverableService;

    @MockitoBean
    private DeliverableTypeService deliverableTypeService;

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void macondoLabConfiguresTheDeliverablesOfATrack() throws Exception {
        when(deliverableTypeService.create(any())).thenReturn(type());
        when(deliverableTypeService.list(eq(1), eq(Track.INNPRENDE_I))).thenReturn(List.of(type()));

        mockMvc.perform(post("/api/v1/deliverable-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TYPE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("DOCUMENT"))
                .andExpect(jsonPath("$.required").value(true));
        mockMvc.perform(get("/api/v1/deliverable-types").param("editionId", "1").param("track", "INNPRENDE_I"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Póster de investigación"));
        mockMvc.perform(delete("/api/v1/deliverable-types/5")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentsReadTheDeliverablesButDoNotConfigureThem() throws Exception {
        when(deliverableTypeService.list(any(), any())).thenReturn(List.of(type()));

        mockMvc.perform(get("/api/v1/deliverable-types").param("editionId", "1").param("track", "INNPRENDE_I"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/deliverable-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TYPE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/deliverable-types/5")).andExpect(status().isForbidden());
    }

    @Test
    void withoutASessionThereAreNoDeliverables() throws Exception {
        mockMvc.perform(get("/api/v1/deliverable-types").param("editionId", "1").param("track", "INNPRENDE_I"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/10/deliverables")).andExpect(status().isUnauthorized());

        verifyNoInteractions(deliverableService, deliverableTypeService);
    }

    @Test
    @WithMockUser(username = "ana@unisimon.edu.co", roles = "STUDENT")
    void theTeamUploadsAFileForADeliverable() throws Exception {
        when(deliverableService.upload(eq(10), eq(5), any(), eq("ana@unisimon.edu.co")))
                .thenReturn(List.of(group(true)));

        mockMvc.perform(multipart("/api/v1/projects/10/deliverables")
                        .file(new MockMultipartFile("file", "poster.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("deliverableTypeId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].complete").value(true))
                .andExpect(jsonPath("$[0].files[0].fileName").value("poster.pdf"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aFullDeliverableIs409() throws Exception {
        when(deliverableService.upload(any(), any(), any(), any()))
                .thenThrow(new ConflictException(
                        "\"Póster\" admite un solo archivo. Quita el que subiste para reemplazarlo"));

        mockMvc.perform(multipart("/api/v1/projects/10/deliverables")
                        .file(new MockMultipartFile("file", "poster.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("deliverableTypeId", "5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("\"Póster\" admite un solo archivo. Quita el que subiste para reemplazarlo"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void removingAFileGivesBackTheDeliverables() throws Exception {
        when(deliverableService.delete(eq(10), eq(3), any())).thenReturn(List.of(group(false)));

        mockMvc.perform(delete("/api/v1/projects/10/deliverables/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].complete").value(false))
                .andExpect(jsonPath("$[0].files").isEmpty());
    }

    @Test
    @WithMockUser(username = "ana@unisimon.edu.co", roles = "STUDENT")
    void theTeamRegistersALinkForALinkDeliverable() throws Exception {
        when(deliverableService.submitLink(eq(10), any(), eq("ana@unisimon.edu.co")))
                .thenReturn(List.of(new DeliverableGroupResponse(
                        type(),
                        List.of(new DeliverableResponse(
                                4, null, null, null, null, "https://youtu.be/abc", "Ana Pérez", LocalDateTime.now())),
                        true)));

        mockMvc.perform(post("/api/v1/projects/10/deliverables/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliverableTypeId\":5,\"url\":\"https://youtu.be/abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].files[0].url").value("https://youtu.be/abc"))
                .andExpect(jsonPath("$[0].files[0].fileId").isEmpty());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aLinkMustBeAnHttpAddress() throws Exception {
        mockMvc.perform(post("/api/v1/projects/10/deliverables/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliverableTypeId\":5,\"url\":\"youtu.be/abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.url").exists());
        mockMvc.perform(post("/api/v1/projects/10/deliverables/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.deliverableTypeId").exists());

        verifyNoInteractions(deliverableService);
    }

    @Test
    @WithMockUser(username = "coordinacion@unisimon.edu.co", roles = "MACONDOLAB")
    void macondoLabUploadsAndRemovesTheTemplate() throws Exception {
        when(deliverableTypeService.uploadTemplate(eq(5), any(), eq("coordinacion@unisimon.edu.co")))
                .thenReturn(type("11111111-0000-4000-8000-000000000000", "poster.pptx"));
        when(deliverableTypeService.deleteTemplate(5)).thenReturn(type());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/deliverable-types/5/template")
                        .file(new MockMultipartFile("file", "poster.pptx", "application/octet-stream", TestData.PPTX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateFileId").value("11111111-0000-4000-8000-000000000000"))
                .andExpect(jsonPath("$.templateFileName").value("poster.pptx"));
        mockMvc.perform(delete("/api/v1/deliverable-types/5/template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateFileId").isEmpty());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void onlyManagementTouchesTemplates() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/deliverable-types/5/template")
                        .file(new MockMultipartFile("file", "poster.pdf", "application/pdf", TestData.PDF)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/deliverable-types/5/template")).andExpect(status().isForbidden());

        verifyNoInteractions(deliverableTypeService);
    }

    private static DeliverableTypeResponse type() {
        return type(null, null);
    }

    private static DeliverableTypeResponse type(String templateFileId, String templateFileName) {
        return new DeliverableTypeResponse(
                5,
                1,
                Track.INNPRENDE_I,
                "Póster de investigación",
                "Formato oficial, en PDF.",
                DeliverableKind.DOCUMENT,
                true,
                1,
                1,
                templateFileId,
                templateFileName,
                null,
                null,
                null);
    }

    private static DeliverableGroupResponse group(boolean withFile) {
        List<DeliverableResponse> files = withFile
                ? List.of(new DeliverableResponse(
                        3,
                        "8a5f1f2e-0000-4000-8000-000000000000",
                        "poster.pdf",
                        "application/pdf",
                        1024,
                        null,
                        "Ana Pérez",
                        LocalDateTime.now()))
                : List.of();
        return new DeliverableGroupResponse(type(), files, withFile);
    }
}
