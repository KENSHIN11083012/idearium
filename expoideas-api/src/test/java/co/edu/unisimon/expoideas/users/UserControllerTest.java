package co.edu.unisimon.expoideas.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import co.edu.unisimon.expoideas.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** La propia cuenta (/users/me): sesión obligatoria y siempre el correo de la sesión. */
@SecuredWebMvcTest(UserController.class)
@WithMockUser(username = UserControllerTest.EMAIL, roles = "STUDENT")
class UserControllerTest {

    static final String EMAIL = "ana@unisimon.edu.co";

    private static final MockMultipartFile PHOTO = new MockMultipartFile("file", "yo.png", "image/png", TestData.PNG);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountService accountService;

    // ── Sesión ──────────────────────────────────────────────────────────────

    @Test
    @WithAnonymousUser
    void withoutSessionIs401ProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Debes iniciar sesión para acceder a este recurso"));

        verifyNoInteractions(accountService);
    }

    // ── Perfil ──────────────────────────────────────────────────────────────

    @Test
    void profileUsesTheSessionEmail() throws Exception {
        when(accountService.getProfile(EMAIL)).thenReturn(response(7, null));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void updateProfileUsesTheSessionEmail() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ana","lastName":"Pérez","campusId":1,"facultyId":2,"academicProgramId":null}
                                """))
                .andExpect(status().isOk());

        verify(accountService).updateProfile(EMAIL, new ProfileUpdateRequest("Ana", "Pérez", 1, 2, null));
    }

    @Test
    void affiliationIsNotRequiredByTheRequestItself() throws Exception {
        // Que la exija o no depende del rol: lo decide el servicio.
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Luis\",\"lastName\":\"Gómez\"}"))
                .andExpect(status().isOk());

        verify(accountService).updateProfile(EMAIL, new ProfileUpdateRequest("Luis", "Gómez", null, null, null));
    }

    @Test
    void serviceFieldErrorsAre400WithFields() throws Exception {
        when(accountService.updateProfile(eq(EMAIL), any()))
                .thenThrow(new InvalidFieldsException("facultyId", "La facultad es obligatoria"));

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ana\",\"lastName\":\"Pérez\",\"campusId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Datos inválidos"))
                .andExpect(jsonPath("$.fields.facultyId").value("La facultad es obligatoria"));
    }

    @Test
    void blankNamesAre400() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\" \",\"lastName\":\"Pérez\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.firstName").value("Los nombres son obligatorios"));

        verifyNoInteractions(accountService);
    }

    // ── Contraseña y autorización de datos ─────────────────────────────────

    @Test
    void changingThePasswordAnswersWithTheTokenThatKeepsThisSession() throws Exception {
        when(accountService.changePassword(eq(EMAIL), any())).thenReturn("token-nuevo");

        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"Vieja#2026\",\"newPassword\":\"Nueva#2026\",\"confirmPassword\":\"Nueva#2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-nuevo"));

        verify(accountService)
                .changePassword(EMAIL, new PasswordChangeRequest("Vieja#2026", "Nueva#2026", "Nueva#2026"));
    }

    @Test
    void wrongCurrentPasswordIsAFieldError() throws Exception {
        doThrow(new InvalidFieldsException("currentPassword", "La contraseña actual es incorrecta."))
                .when(accountService)
                .changePassword(eq(EMAIL), any());

        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"Mala#2026\",\"newPassword\":\"Nueva#2026\",\"confirmPassword\":\"Nueva#2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.currentPassword").value("La contraseña actual es incorrecta."));
    }

    @Test
    void weakNewPasswordIs400() throws Exception {
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"Vieja#2026\",\"newPassword\":\"123\",\"confirmPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());

        verifyNoInteractions(accountService);
    }

    @Test
    void dataConsentMustBeTrue() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/data-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataConsent\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.dataConsent")
                        .value("Debes autorizar el tratamiento de tus datos para usar la plataforma"));
        mockMvc.perform(put("/api/v1/users/me/data-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataConsent\":true}"))
                .andExpect(status().isNoContent());

        verify(accountService).giveDataConsent(EMAIL);
    }

    // ── Foto de perfil ──────────────────────────────────────────────────────

    @Test
    void uploadPhotoAsMultipart() throws Exception {
        when(accountService.updatePhoto(eq(EMAIL), any()))
                .thenReturn(response(7, "0b0f3f7e-8c1a-4c55-9d3e-2f1a6b7c8d9e"));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/photo").file(PHOTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").value("0b0f3f7e-8c1a-4c55-9d3e-2f1a6b7c8d9e"));
    }

    @Test
    void uploadWithoutTheFilePartIs400() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/photo").param("otro", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el archivo en la petición"));

        verifyNoInteractions(accountService);
    }

    @Test
    void uploadAsJsonIs415() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/photo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://sitio-externo.com/foto.png\"}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(accountService);
    }

    @Test
    void invalidFormatIsAFieldError() throws Exception {
        when(accountService.updatePhoto(eq(EMAIL), any()))
                .thenThrow(new InvalidFieldsException("file", "Formato no permitido. Usa un archivo JPG, PNG o WEBP."));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/photo").file(PHOTO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.file").value("Formato no permitido. Usa un archivo JPG, PNG o WEBP."));
    }

    @Test
    void deletePhotoIs204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/photo")).andExpect(status().isNoContent());

        verify(accountService).deletePhoto(EMAIL);
    }

    private static UserResponse response(int id, String photoId) {
        return new UserResponse(
                id,
                "Ana",
                "Pérez",
                EMAIL,
                Role.STUDENT,
                photoId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                null);
    }
}
