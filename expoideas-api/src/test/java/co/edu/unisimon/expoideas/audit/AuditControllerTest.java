package co.edu.unisimon.expoideas.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.AuditableAction.Target;
import co.edu.unisimon.expoideas.common.PageResponse;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** El rastro de auditoría (/admin/audit): quién entra y el contrato HTTP. */
@SecuredWebMvcTest(AuditController.class)
class AuditControllerTest {

    private static final String AUDIT = "/api/v1/admin/audit";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdminReadsTheTraceWithItsFilters() throws Exception {
        AuditEventResponse row = new AuditEventResponse(
                7L,
                LocalDateTime.of(2026, 11, 20, 10, 15),
                3,
                "coordinacion@unisimon.edu.co",
                Action.ROLE_CHANGED,
                Action.ROLE_CHANGED.label(),
                Target.ACCOUNT,
                12,
                "ana@unisimon.edu.co",
                "Estudiante → Profesor");
        when(auditService.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(row), 2, 20, 41, 3));

        mockMvc.perform(get(AUDIT + "?action=ROLE_CHANGED&from=2026-11-01&to=2026-11-30&page=2&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("ROLE_CHANGED"))
                .andExpect(jsonPath("$.items[0].actionLabel").value("Cambio de rol"))
                .andExpect(jsonPath("$.items[0].occurredAt").value("2026-11-20T10:15:00"))
                .andExpect(jsonPath("$.items[0].targetType").value("ACCOUNT"))
                .andExpect(jsonPath("$.totalItems").value(41))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(auditService).search(Action.ROLE_CHANGED, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 2, 20);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void withoutFiltersItAsksForTheFirstPage() throws Exception {
        when(auditService.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));

        mockMvc.perform(get(AUDIT)).andExpect(status().isOk());

        verify(auditService).search(null, null, null, 0, AuditService.DEFAULT_SIZE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?action=NO_EXISTE", "?from=ayer", "?to=2026-13-40", "?page=dos"})
    @WithMockUser(roles = "ADMIN")
    void aFilterThatCannotBeReadIsABadRequest(String query) throws Exception {
        mockMvc.perform(get(AUDIT + query)).andExpect(status().isBadRequest());

        verifyNoInteractions(auditService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MACONDOLAB", "TEACHER", "JUDGE", "STUDENT"})
    void onlyAnAdminReadsIt(String role) throws Exception {
        // En el rastro está también lo que hace MacondoLab: no lo lee quien aparece en él.
        mockMvc.perform(get(AUDIT).with(user("alguien@unisimon.edu.co").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auditService);
    }

    @Test
    void withoutASessionItIsNotRead() throws Exception {
        mockMvc.perform(get(AUDIT)).andExpect(status().isUnauthorized());

        verifyNoInteractions(auditService);
    }
}
