package co.edu.unisimon.expoideas.auth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import co.edu.unisimon.expoideas.users.AccountLinkService;
import co.edu.unisimon.expoideas.users.OnboardingStep;
import co.edu.unisimon.expoideas.users.PasswordRecoveryResetRequest;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.UserAccountService;
import co.edu.unisimon.expoideas.users.UserResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Login y registro: públicos, con la validación del cuerpo. */
@SecuredWebMvcTest(AuthController.class)
class AuthControllerTest {

    private static final String EMAIL = "ana@unisimon.edu.co";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserAccountService accountService;

    @MockitoBean
    private AccountLinkService accountLinks;

    // ── Login ───────────────────────────────────────────────────────────────

    @Test
    void wrongCredentialsAre401WithTheSameMessage() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"Mala#2026\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void whenTheDatabaseDoesNotAnswerItIs503AndNotASessionProblem() throws Exception {
        when(authService.login(any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"Segura#2026\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("no está disponible")));
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());

        verifyNoInteractions(authService);
    }

    // ── Registro ────────────────────────────────────────────────────────────

    @Test
    void validRegistrationIs201WithTheProfileStepPending() throws Exception {
        when(accountService.register(any())).thenReturn(registered());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(EMAIL, "Segura#2026", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.firstName").isEmpty())
                .andExpect(jsonPath("$.pendingSteps", contains("COMPLETE_PROFILE")));
    }

    @Test
    void institutionalDomainIsAcceptedInUpperCase() throws Exception {
        when(accountService.register(any())).thenReturn(registered());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Ana.Perez@UNISIMON.EDU.CO", "Segura#2026", true)))
                .andExpect(status().isCreated());
    }

    @Test
    void registrationRequiresDataConsent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(EMAIL, "Segura#2026", false)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Datos inválidos"))
                .andExpect(jsonPath("$.fields.dataConsent").exists());

        verifyNoInteractions(accountService);
    }

    @Test
    void registrationRequiresAnInstitutionalEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("ana@gmail.com", "Segura#2026", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("El correo debe terminar en @unisimon.edu.co"));
    }

    @Test
    void registrationRequiresAStrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(EMAIL, "123456", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void aPasswordThatDoesNotFitInBCryptIsRejectedAsAFieldError() throws Exception {
        // 40 eñes son 42 caracteres pero 82 bytes, y BCrypt solo lee 72.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(EMAIL, "ñ".repeat(40) + "-1", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());

        verifyNoInteractions(accountService);
    }

    @Test
    void aPasswordOfExactly72BytesIsAccepted() throws Exception {
        when(accountService.register(any())).thenReturn(registered());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(EMAIL, "ñ".repeat(35) + "-1", true)))
                .andExpect(status().isCreated());
    }

    // ── Enlaces enviados por correo ─────────────────────────────────────────

    @Test
    void openingTheVerificationLinkNeedsNoSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isNoContent());

        verify(accountLinks).verifyEmail("abc");
    }

    @Test
    void askingForTheLinkAgainNeedsASession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")).andExpect(status().isUnauthorized());

        verifyNoInteractions(accountLinks);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void withASessionTheLinkIsSentAgain() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")).andExpect(status().isNoContent());

        verify(accountLinks).resendVerification(EMAIL);
    }

    @Test
    void passwordRecoveryIsAcceptedWithoutSayingIfTheAccountExists() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(accountLinks).requestPasswordRecovery(EMAIL);
    }

    @Test
    void theNewPasswordFromARecoveryLinkFollowsTheSameRules() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"123\",\"confirmPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
        verifyNoInteractions(accountLinks);

        mockMvc.perform(
                        post("/api/v1/auth/password-reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\"abc\",\"newPassword\":\"Nueva#2026\",\"confirmPassword\":\"Nueva#2026\"}"))
                .andExpect(status().isNoContent());
        verify(accountLinks).resetPassword(new PasswordRecoveryResetRequest("abc", "Nueva#2026", "Nueva#2026"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición no es un JSON válido"));
    }

    @Test
    void takenEmailIs409() throws Exception {
        when(accountService.register(any()))
                .thenThrow(new ConflictException("El correo institucional ya está registrado."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(EMAIL, "Segura#2026", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El correo institucional ya está registrado."));
    }

    /** Registro válido salvo lo que se varíe: solo correo, contraseña y autorización. */
    private static String registration(String email, String password, boolean dataConsent) {
        return """
                {"email":"%s","password":"%s","dataConsent":%s}
                """.formatted(email, password, dataConsent);
    }

    /** Cuenta recién registrada: sin nombre ni adscripción, con el perfil pendiente. */
    private static UserResponse registered() {
        return new UserResponse(
                1,
                null,
                null,
                EMAIL,
                Role.STUDENT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(OnboardingStep.COMPLETE_PROFILE),
                false,
                null);
    }
}
