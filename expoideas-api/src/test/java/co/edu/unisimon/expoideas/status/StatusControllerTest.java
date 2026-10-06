package co.edu.unisimon.expoideas.status;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.status.StatusResponse.Disk;
import co.edu.unisimon.expoideas.status.StatusResponse.Mail;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** El estado de la instalación (/admin/status): quién entra y el contrato HTTP. */
@SecuredWebMvcTest(StatusController.class)
class StatusControllerTest {

    private static final String STATUS = "/api/v1/admin/status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusService statusService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdminSeesVersionDiskAndMail() throws Exception {
        when(statusService.status())
                .thenReturn(new StatusResponse(
                        "211162e",
                        LocalDateTime.of(2026, 11, 20, 10, 0),
                        new Disk(40_000_000_000L, 100_000_000_000L, 60, false),
                        new Mail(true, true, 2, LocalDateTime.of(2026, 11, 20, 8, 30))));

        mockMvc.perform(get(STATUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("211162e"))
                .andExpect(jsonPath("$.now").value("2026-11-20T10:00:00"))
                .andExpect(jsonPath("$.disk.usedPercent").value(60))
                .andExpect(jsonPath("$.disk.low").value(false))
                .andExpect(jsonPath("$.mail.configured").value(true))
                .andExpect(jsonPath("$.mail.failedLast24Hours").value(2))
                .andExpect(jsonPath("$.mail.lastFailureAt").value("2026-11-20T08:30:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MACONDOLAB", "TEACHER", "JUDGE", "STUDENT"})
    void onlyAnAdminReadsIt(String role) throws Exception {
        mockMvc.perform(get(STATUS).with(user("alguien@unisimon.edu.co").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statusService);
    }

    @Test
    void withoutASessionItIsNotRead() throws Exception {
        mockMvc.perform(get(STATUS)).andExpect(status().isUnauthorized());
    }
}
