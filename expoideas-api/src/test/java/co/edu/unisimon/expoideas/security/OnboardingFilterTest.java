package co.edu.unisimon.expoideas.security;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.catalogs.CatalogController;
import co.edu.unisimon.expoideas.catalogs.CatalogService;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.PersonalDataService;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserAccountService;
import co.edu.unisimon.expoideas.users.UserAdminController;
import co.edu.unisimon.expoideas.users.UserController;
import co.edu.unisimon.expoideas.users.UserManagementService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Con el primer ingreso pendiente, la cuenta solo puede resolverlo. */
@SecuredWebMvcTest({UserController.class, UserAdminController.class, CatalogController.class})
class OnboardingFilterTest {

    private static final String EMAIL = "coordinacion@unisimon.edu.co";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountService accountService;

    @MockitoBean
    private UserManagementService managementService;

    @MockitoBean
    private PersonalDataService personalData;

    @MockitoBean
    private CatalogService catalogService;

    /** Cuenta MacondoLab recién creada: contraseña temporal y sin autorización de datos. */
    private static RequestPostProcessor newAccount() {
        User user = TestData.user(3, EMAIL, Role.MACONDOLAB);
        user.setMustChangePassword(true);
        user.setDataConsent(false);
        return user(new UserPrincipal(user));
    }

    private static RequestPostProcessor upToDateAccount() {
        return user(new UserPrincipal(TestData.user(3, EMAIL, Role.MACONDOLAB)));
    }

    @Test
    void everythingElseIs403WithThePendingSteps() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(newAccount()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Antes de continuar, completa tu primer ingreso."))
                .andExpect(jsonPath("$.pendingSteps", contains("CHANGE_PASSWORD", "DATA_CONSENT")));
        mockMvc.perform(get("/api/v1/users/me/data-export").with(newAccount())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sectors")
                        .with(newAccount())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Textil\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountService, managementService, catalogService);
    }

    @Test
    void theStepsThemselvesAreAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(newAccount())).andExpect(status().isOk());
        // Completar el perfil (nombre y adscripción) también es un paso del primer ingreso.
        mockMvc.perform(put("/api/v1/users/me")
                        .with(newAccount())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Carla\",\"lastName\":\"Díaz\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .with(newAccount())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"Temporal#2026\",\"newPassword\":\"Propia#2026\",\"confirmPassword\":\"Propia#2026\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/me/data-consent")
                        .with(newAccount())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataConsent\":true}"))
                .andExpect(status().isNoContent());

        verify(accountService).updateProfile(eq(EMAIL), any());
        verify(accountService).changePassword(eq(EMAIL), any());
        verify(accountService).giveDataConsent(EMAIL);
    }

    @Test
    void withoutAPhotoTheAccountOnlyGetsToUploadIt() throws Exception {
        // La foto es obligatoria para todos los roles: una cuenta que ya usaba la plataforma también la debe.
        User account = TestData.user(3, EMAIL, Role.MACONDOLAB);
        account.setPhoto(null);
        RequestPostProcessor withoutPhoto = user(new UserPrincipal(account));

        mockMvc.perform(get("/api/v1/admin/users").with(withoutPhoto))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.pendingSteps", contains("PROFILE_PHOTO")));
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/photo")
                        .file(new MockMultipartFile("file", "yo.png", "image/png", TestData.PNG))
                        .with(withoutPhoto))
                .andExpect(status().isOk());

        verify(accountService).updatePhoto(eq(EMAIL), any());
        verifyNoInteractions(managementService);
    }

    @Test
    void publicReadsStayPublic() throws Exception {
        when(catalogService.listCampuses()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/campuses").with(newAccount())).andExpect(status().isOk());
    }

    @Test
    void upToDateAccountUsesThePlatform() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(upToDateAccount())).andExpect(status().isOk());
    }

    @Test
    void roleRulesStillApplyWhileOnboarding() throws Exception {
        // Resolver el primer ingreso no abre rutas que el rol no tiene.
        User judge = TestData.user(4, "marta@empresa.com", Role.JUDGE);
        judge.setMustChangePassword(true);
        RequestPostProcessor newJudge = user(new UserPrincipal(judge));

        mockMvc.perform(put("/api/v1/users/me/data-consent")
                        .with(newJudge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataConsent\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/campuses")
                        .with(newJudge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cúcuta\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.pendingSteps").exists());
    }
}
