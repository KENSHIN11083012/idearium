package co.edu.unisimon.expoideas.jury;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import co.edu.unisimon.expoideas.users.Role;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Jurados: la gestión asigna y quita; cada sesión consulta lo suyo. */
@SecuredWebMvcTest(JuryController.class)
class JuryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JuryService juryService;

    @Test
    @WithMockUser(username = "coordinacion@unisimon.edu.co", roles = "MACONDOLAB")
    void managementAssignsListsAndRemovesJurors() throws Exception {
        JurorResponse marta =
                new JurorResponse(3, 8, "Marta Ríos", "marta@empresa.com", Role.JUDGE, LocalDateTime.now());
        when(juryService.assign(eq(10), eq("coordinacion@unisimon.edu.co"), any()))
                .thenReturn(marta);
        when(juryService.list(10)).thenReturn(List.of(marta));

        mockMvc.perform(post("/api/v1/projects/10/jurors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"marta@empresa.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Marta Ríos"))
                .andExpect(jsonPath("$.role").value("JUDGE"));
        mockMvc.perform(get("/api/v1/projects/10/jurors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(8));
        mockMvc.perform(delete("/api/v1/projects/10/jurors/8")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void aBadJurorComesBackAsAFieldError() throws Exception {
        when(juryService.assign(any(), any(), any()))
                .thenThrow(new InvalidFieldsException(
                        "email", "El profesor del grupo no puede ser jurado de su propio proyecto"));

        mockMvc.perform(post("/api/v1/projects/10/jurors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carlos@unisimon.edu.co\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email")
                        .value("El profesor del grupo no puede ser jurado de su propio proyecto"));
        mockMvc.perform(post("/api/v1/projects/10/jurors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-un-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    @WithMockUser(username = "marta@empresa.com", roles = "JUDGE")
    void aJurorSeesTheirProjectsButDoesNotAssign() throws Exception {
        when(juryService.myProjects("marta@empresa.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jury/projects")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/10/jurors")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/projects/10/jurors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"otra@empresa.com\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/projects/10/jurors/8")).andExpect(status().isForbidden());
    }

    @Test
    void withoutASessionThereIsNothing() throws Exception {
        mockMvc.perform(get("/api/v1/jury/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/10/jurors")).andExpect(status().isUnauthorized());

        verifyNoInteractions(juryService);
    }
}
