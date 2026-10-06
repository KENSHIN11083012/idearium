package co.edu.unisimon.expoideas.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** El listado de la cátedra es de la gestión: cargar, ver y limpiar. */
@SecuredWebMvcTest(RosterController.class)
class RosterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RosterService rosterService;

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void managementUploadsListsAndClears() throws Exception {
        when(rosterService.importCsv(any()))
                .thenReturn(new RosterImportResponse(
                        2,
                        1,
                        0,
                        3,
                        List.of(new RosterImportResponse.RejectedRow(4, "ana@gmail.com", "No es institucional"))));
        when(rosterService.list())
                .thenReturn(
                        List.of(new RosterEntryResponse(1, "ana@unisimon.edu.co", Role.STUDENT, "Ana", "Pérez", true)));

        mockMvc.perform(multipart("/api/v1/admin/roster")
                        .file(new MockMultipartFile("file", "listado.csv", "text/csv", "correo;rol\n".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.rejected[0].line").value(4));
        mockMvc.perform(get("/api/v1/admin/roster"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].registered").value(true));
        mockMvc.perform(delete("/api/v1/admin/roster/1")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/roster")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void nobodyElseTouchesIt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roster")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/roster")).andExpect(status().isForbidden());
        verifyNoInteractions(rosterService);
    }
}
